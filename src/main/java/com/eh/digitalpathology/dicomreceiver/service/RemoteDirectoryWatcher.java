package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.SharedFolderConfig;
import com.eh.digitalpathology.dicomreceiver.constants.WatchDirectoryConstant;
import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import net.idauto.oss.jcifsng.vfs2.provider.SmbFileSystemConfigBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.auth.StaticUserAuthenticator;
import org.apache.commons.vfs2.impl.DefaultFileSystemConfigBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RemoteDirectoryWatcher {
    private static final Logger logger = LoggerFactory.getLogger( RemoteDirectoryWatcher.class );

    private final SharedFolderConfig sharedFolderConfig;
    private final RemoteDirectoryService remoteDirectoryService;
    private final AtomicInteger processedFileCount = new AtomicInteger( 0 );
    private long lastLogTime = System.currentTimeMillis( );
    private final RedisClient redisClient;
    private final ScheduledExecutorService scheduler;
    private static final String SMB_CLIENT_MAX_VERSION = "SMB311";
    private static final String SMB_CLIENT_MIN_VERSION = "SMB202";

    private FileSystemManager fsManager;
    private FileSystemOptions fsOptions;
    private String smbUrl;

    public RemoteDirectoryWatcher ( SharedFolderConfig sharedFolderConfig, RemoteDirectoryService remoteDirectoryService, RedisClient redisClient, @Qualifier( "remoteDirectoryWatcherScheduledExecutor" ) ScheduledExecutorService scheduler ) {
        this.sharedFolderConfig = sharedFolderConfig;
        this.remoteDirectoryService = remoteDirectoryService;
        this.redisClient = redisClient;
        this.scheduler = scheduler;
    }

    public void watchSharedDirectory ( ) {
        logger.info( "watchSharedDirectory :: Starting directory watch..." );
        try {
            Properties jcifsProperties = new Properties( );
            jcifsProperties.setProperty( WatchDirectoryConstant.SMB_MIN_VERSION, SMB_CLIENT_MIN_VERSION );
            jcifsProperties.setProperty( WatchDirectoryConstant.SMB_MAX_VERSION, SMB_CLIENT_MAX_VERSION );

            CIFSContext jcifsContext = new BaseContext( new PropertyConfiguration( jcifsProperties ) );
            fsOptions = new FileSystemOptions( );

            if ( !StringUtils.isEmpty( sharedFolderConfig.getUsername( ) ) && !StringUtils.isEmpty( sharedFolderConfig.getPassword( ) ) ) {
                StaticUserAuthenticator auth = new StaticUserAuthenticator( "", sharedFolderConfig.getUsername( ), sharedFolderConfig.getPassword( ) );
                DefaultFileSystemConfigBuilder.getInstance( ).setUserAuthenticator( fsOptions, auth );
            }

            SmbFileSystemConfigBuilder.getInstance( ).setCIFSContext( fsOptions, jcifsContext );
            smbUrl = new URIBuilder( ).setScheme( WatchDirectoryConstant.SMB ).setHost( sharedFolderConfig.getServername( ) ).setPath( sharedFolderConfig.getSharepath( ) ).toString( );
            fsManager = VFS.getManager( );
            resolveSharedFolder( );
        } catch ( Exception e ) {
            logger.error( "watchSharedDirectory :: Error initializing watcher for server: {} and share: {}, {}", sharedFolderConfig.getServername( ), sharedFolderConfig.getSharepath( ), e.getMessage() );
        }
    }

    private void resolveSharedFolder ( ) {
        logger.info( "resolveSharedFolder :: Monitoring directory: {}", smbUrl );
        scheduler.scheduleWithFixedDelay( ( ) -> {
            try {
                try ( FileObject remoteDirectory = fsManager.resolveFile( smbUrl, fsOptions ) ) {
                    scanDirectoryRecursively( remoteDirectory );
                }
            } catch ( Exception e ) {
                logger.error( "resolveSharedFolder :: Error during scheduled scan, {}", e.getMessage() );
            }
        }, 0, 5, TimeUnit.SECONDS );
    }

    private void scanDirectoryRecursively(FileObject directory) {
        try {
            directory.refresh();
            FileObject[] children = directory.getChildren();
            if (children == null || children.length == 0) {
                logger.error("scanDirectoryRecursively :: No children in {}", directory.getName().getPath());
                return;
            }

            for (FileObject child : children) {
                processChild(child);
            }

            logScanSummaryIfDue();

        } catch (Exception e) {
            logger.error("scanDirectoryRecursively :: Error scanning directory: {}, {}",
                    directory.getName(), e.getMessage());
        }
    }

    private void processChild(FileObject child) {
        if (child == null) return;

        final String path = child.getName().getPath();
        final String baseName = child.getName().getBaseName();

        if (shouldSkipByName(baseName)) {
            logger.debug("scanDirectoryRecursively :: Skipping by name rule: {}", path);
            return;
        }

        FileType type = getTypeSafe(child);
        if (type == null) {
            // Type unavailable; already logged in getTypeSafe
            return;
        }

        if (isHiddenSafe(child)) {
            logger.debug("scanDirectoryRecursively :: Skipping hidden item: {}", path);
            return;
        }

        if (type == FileType.FOLDER) {
            scanDirectoryRecursively(child);
            return;
        }

        if (type == FileType.FILE) {
            handleFile(child);
        }
    }

    private boolean shouldSkipByName(String baseName) {
        return isNameHidden(baseName) || isTempOrPartial(baseName);
    }

    private FileType getTypeSafe(FileObject child) {
        try {
            return child.getType();
        } catch (org.apache.commons.vfs2.FileSystemException fse) {
            logger.debug("scanDirectoryRecursively :: Unable to get type for {}: {}",
                    child.getName().getPath(), fse.getMessage());
            return null;
        }
    }

    private boolean isHiddenSafe(FileObject child) {
        try {
            return child.isHidden(); // may throw on SMB
        } catch (org.apache.commons.vfs2.FileSystemException fse) {
            logger.debug("scanDirectoryRecursively :: Hidden check failed for {}: {}",
                    child.getName().getPath(), fse.toString());
            // Conservatively treat as not hidden to avoid skipping valid files on transient errors.
            return false;
        }
    }

    private void logScanSummaryIfDue() {
        long now = System.currentTimeMillis();
        if (now - lastLogTime > 60_000) {
            logger.info("scanDirectoryRecursively :: Scan Summary: Total processed files: {}",
                    processedFileCount.get());
            lastLogTime = now;
        }
    }

    private boolean isNameHidden(String baseName) {
        return baseName.startsWith(".") || baseName.startsWith("~$") || baseName.equalsIgnoreCase("thumbs.db");
    }


    private boolean isTempOrPartial ( String baseName ) {
        String lower = baseName.toLowerCase( );
        return lower.endsWith( ".tmp" ) || lower.endsWith( ".part" ) || lower.startsWith( "~$" ) || lower.startsWith( "._" ) || lower.endsWith( ".crdownload" );
    }

    private void handleFile(FileObject file) {
        final String fileKey = file.getName().getPath();

        // Guard 1: Already processed
        if (redisClient.isFileProcessed(fileKey)) {
            return;
        }

        // Compute TTL once
        final Duration ttl = computeAdaptiveTtl(file);

        // Guard 2: Could not acquire lock
        if (!redisClient.tryLockFile(fileKey, ttl)) {
            logger.debug("handleFile :: File is being processed by another thread: {}", fileKey);
            // Will retry in next scan cycle
            return;
        }

        try {
            // Guard 3: File not stable
            if (!isFileStable(file)) {
                logger.info("handleFile :: File not stable yet: {}", fileKey);
                // let next scan retry earlier
                return;
            }

            // Stable file path
            logger.info("handleFile :: New stable file detected: {}", file.getName());
            CompletableFuture<Boolean> future = remoteDirectoryService.processFileEvent(file, smbUrl);

            // Completion: centralize all outcomes in one place
            attachCompletionHandler(future, fileKey);

        } catch (Exception e) {
            logger.error("handleFile :: Error processing file: {}, {}", fileKey, e.getMessage());
            // Ensure lock is released on synchronous failure paths
            redisClient.releaseFileLock(fileKey);
        }
        // Note: For async path we release in the completion handler; otherwise
        // we rely on Redis TTL as a safety net if the process crashes.
    }
    private void attachCompletionHandler(CompletableFuture<Boolean> future, String fileKey) {
        future.whenComplete((ok, ex) -> {
            try {
                if (ex != null) {
                    logger.error("handleFile :: Error processing file asynchronously: {}, {}", fileKey, ex.getMessage());
                    redisClient.releaseFileLock(fileKey);
                    return;
                }

                if (Boolean.TRUE.equals(ok)) {
                    redisClient.markFileAsProcessed(fileKey);
                    processedFileCount.incrementAndGet();
                    redisClient.releaseFileLock(fileKey);
                    return;
                }

                logger.warn("handleFile :: Processing returned false for {}", fileKey);
                redisClient.releaseFileLock(fileKey);

            } catch (Exception e) {
                logger.error("handleFile :: Exception in completion handler for file: {}, {}", fileKey, e.getMessage());
                redisClient.releaseFileLock(fileKey);
            }
        });
    }

    private Duration computeAdaptiveTtl ( FileObject file ) {
        try {
            long sizeBytes = file.getContent( ).getSize( );
            long sizeMB = Math.max( 1, sizeBytes / ( 1024 * 1024 ) );
            double expectedMBps = 80.0; // Adjust to your environment (typical 1GbE ~ 60–110 MB/s)
            long seconds = (long) Math.ceil( ( sizeMB / expectedMBps ) * 1.5 );
            long clamped = Math.max( 120, Math.min( 3600, seconds ) ); // min 2 min, max 60 min
            return Duration.ofSeconds( clamped );
        } catch ( Exception e ) {
            logger.warn( "computeAdaptiveTtl :: Unable to read size; using default TTL, {}", e.getMessage() );
            return Duration.ofMinutes( 5 );
        }
    }

    private boolean isFileStable ( FileObject file ) {
        try {
            long size1 = file.getContent( ).getSize( );
            long lm1 = file.getContent( ).getLastModifiedTime( );

            // Size-aware wait: shorter for small, longer for big

            long waitMs;

            if (size1 > (1L << 30)) {          // > 1 GB
                waitMs = 20_000;
            } else if (size1 > (100L << 20)) { // > 100 MB
                waitMs = 10_000;
            } else {
                waitMs = 3_000;
            }

            Thread.sleep( waitMs );

            file.refresh( );
            long size2 = file.getContent( ).getSize( );
            long lm2 = file.getContent( ).getLastModifiedTime( );
            return size1 == size2 && lm1 == lm2;
        } catch ( InterruptedException ie ) {
            Thread.currentThread( ).interrupt( );
            logger.warn( "isFileStable :: Interrupted while waiting. {}", ie.getMessage() );
            return false;
        } catch ( Exception e ) {
            logger.error( "isFileStable :: Error checking file stability {}", e.getMessage() );
            return false;
        }
    }
}
