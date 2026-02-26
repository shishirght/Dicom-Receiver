package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.SharedFolderConfig;
import com.eh.digitalpathology.dicomreceiver.util.CommonUtils;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.concurrent.*;


@Service
@RefreshScope
public class RemoteDirectoryService {

    private static final Logger logger = LoggerFactory.getLogger( RemoteDirectoryService.class );

    @Value( "${max.retries}" )
    private long maxRetries;

    private final CommonUtils commonUtils;
    private final ExecutorService executorService;
    private final SharedFolderConfig sharedFolderConfig;

    private final Semaphore largeCopyLimit = new Semaphore( 2 ); // > 1 GB
    private final Semaphore mediumCopyLimit = new Semaphore( 6 ); // 100 MB – 1 GB

    // Reuse SMB resources per worker thread
    private final ThreadLocal< ShareHandle > shareHolder = new ThreadLocal<>( );
    private final Object shareLock = new Object();

    public RemoteDirectoryService ( CommonUtils commonUtils, @Qualifier( "remoteDirectoryWatcherExecutor" ) ExecutorService executorService, SharedFolderConfig sharedFolderConfig ) {
        this.commonUtils = commonUtils;
        this.executorService = executorService;
        this.sharedFolderConfig = sharedFolderConfig;
    }

    public CompletableFuture< Boolean > processFileEvent ( FileObject file, String smbUrl ) {
        logger.info( "processFileEvent :: Inside processFileEvent method for file: {}", file );
        try {
            String relativeFilePath = file.getName( ).getPath( ).replaceFirst( smbUrl, "" );
            long sizeBytes = safeSize( file );
            Callable< Boolean > task = ( ) -> establishConnectionAndCopy( relativeFilePath, file.getName( ).getBaseName( ), sizeBytes );

            // Size-aware concurrency (small files bypass, medium/large limited)
            Future< Boolean > f = submitWithSizeControl( sizeBytes, task );
            // Wrap into CompletableFuture
            CompletableFuture< Boolean > cf = new CompletableFuture<>( );
            executorService.submit( ( ) -> {
                try {
                    cf.complete( f.get( ) );
                } catch ( ExecutionException ee ) {
                    cf.completeExceptionally( ee.getCause( ) != null ? ee.getCause( ) : ee );
                } catch ( InterruptedException ie ) {
                    Thread.currentThread( ).interrupt( );
                    cf.completeExceptionally( ie );
                }
            } );
            return cf;

        } catch ( Exception e ) {
            logger.error( "processFileEvent :: Exception occurred while processing FileEvent : {}", e.getMessage() );
            CompletableFuture< Boolean > failed = new CompletableFuture<>( );
            failed.completeExceptionally( e );
            return failed;
        }
    }

    private Future< Boolean > submitWithSizeControl ( long sizeBytes, Callable< Boolean > task ) {
        if ( sizeBytes > ( 1L << 30 ) ) { // > 1 GB
            return executorService.submit( ( ) -> {
                largeCopyLimit.acquire( );
                try {
                    return task.call( );
                } finally {
                    largeCopyLimit.release( );
                }
            } );
        } else if ( sizeBytes > ( 100L << 20 ) ) { // > 100 MB
            return executorService.submit( ( ) -> {
                mediumCopyLimit.acquire( );
                try {
                    return task.call( );
                } finally {
                    mediumCopyLimit.release( );
                }
            } );
        } else {
            return executorService.submit( task );
        }
    }

    private long safeSize ( FileObject file ) {
        try {
            return file.getContent( ).getSize( );
        } catch ( Exception e ) {
            logger.warn( "safeSize :: Unable to read size; assuming medium {}", e.getMessage() );
            return 200L << 20; // 200 MB default
        }
    }

    private Boolean establishConnectionAndCopy(String remoteFileRelativePath, String remoteFileName, long sizeBytes) {
        logger.info("establishConnectionAndCopy :: relative path : {}", remoteFileRelativePath);

        ShareHandle handle = ensureShare();
        if (handle == null) {
            logger.error("establishConnectionAndCopy :: Cannot obtain share");
            return false;
        }

        final int maxCopyRetries = Math.min(2, (int) maxRetries);
        final int maxAttempts = maxCopyRetries + 1; // inclusive attempts

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try (File remoteFile = openRemoteFile(handle.share, remoteFileRelativePath)) {
                if (!copyRemoteFileToLocal(remoteFile, remoteFileName, sizeBytes)) {
                    return false;
                }

                // Best-effort delete (non-blocking for success path)
                deleteRemoteBestEffort(handle.share, remoteFileRelativePath);
                return true;

            } catch (Exception e) {
                logger.error("establishConnectionAndCopy :: Attempt {} - Exception during open/copy: {}",
                        attempt, e.getMessage());

                if (attempt == maxAttempts) {
                    logger.error("establishConnectionAndCopy :: Max retries reached for {}", remoteFileName);
                    return false;
                }

                // Prepare next attempt: drop & reacquire share, brief backoff
                closeShareQuietly();
                sleepQuietly(500L);

                handle = ensureShare();
                if (handle == null) {
                    return false;
                }
            }
        }
        return false; // Unreachable due to returns inside loop
    }
    private File openRemoteFile(DiskShare share, String remoteFileRelativePath) {
        return share.openFile(
                remoteFileRelativePath,
                EnumSet.of(AccessMask.GENERIC_READ),
                EnumSet.noneOf(FileAttributes.class),
                EnumSet.of(
                        SMB2ShareAccess.FILE_SHARE_READ,
                        SMB2ShareAccess.FILE_SHARE_DELETE,
                        SMB2ShareAccess.FILE_SHARE_WRITE
                ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.noneOf(SMB2CreateOptions.class)
        );
    }

    private void deleteRemoteBestEffort(DiskShare share, String remoteFileRelativePath) {
        boolean deleted = deleteWithRetry(share, remoteFileRelativePath, 3, 900);
        if (!deleted) {
            logger.info("establishConnectionAndCopy :: Delete deferred for {}", remoteFileRelativePath);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }


    /**
     * Delete with small retry/backoff to handle transient sharing violations
     */
    private boolean deleteWithRetry ( DiskShare share, String remoteFileRelativePath, int maxAttempts, long backoffMs ) {
        for ( int i = 1; i <= Math.max( 1, maxAttempts ); i++ ) {
            try {
                share.rm( remoteFileRelativePath );
                return true;
            } catch ( Exception e ) {
                String msg = String.valueOf( e.getMessage( ) );
                boolean sharingViolation = msg.contains( "STATUS_SHARING_VIOLATION" ) || msg.contains( "0xc0000043" );
                if ( !sharingViolation ) {
                    logger.warn( "deleteWithRetry :: Delete failed (non-sharing) for {}: {}", remoteFileRelativePath, msg );
                    return false;
                }
                logger.warn( "deleteWithRetry :: Sharing violation on attempt {}/{} for {}. Retrying...", i, maxAttempts, remoteFileRelativePath );
                if ( i < maxAttempts ) {
                    try {
                        Thread.sleep( backoffMs );
                    } catch ( InterruptedException ie ) {
                        Thread.currentThread( ).interrupt( );
                        return false;
                    }
                }
            }
        }
        return false;
    }


    private boolean copyRemoteFileToLocal ( File remoteFile, String remoteFileName, long sizeBytes ) {
        logger.info( "copyRemoteFileToLocal :: Copying file: {}", remoteFileName );

        String receivedFiles = commonUtils.getLocalStoragePath( );
        if ( "DICOMDIR".equalsIgnoreCase( remoteFileName ) ) {
            remoteFileName = remoteFileName + "_" + Instant.now( );
        }
        Path localStorageFile = Paths.get( receivedFiles, remoteFileName );

        try ( InputStream smbInputStream = remoteFile.getInputStream( ); ReadableByteChannel inChannel = Channels.newChannel( smbInputStream ); FileChannel outChannel = FileChannel.open( localStorageFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING ) ) {

            long position = 0;
            final long CHUNK = ( sizeBytes > ( 1L << 30 ) ) ? 32L * 1024 * 1024 : 16L * 1024 * 1024;
            long transferred;
            while ( ( transferred = outChannel.transferFrom( inChannel, position, CHUNK ) ) > 0 ) {
                position += transferred;
            }

            logger.info( "copyRemoteFileToLocal :: File copied successfully: {}", remoteFileName );
            return true;

        } catch ( Exception e ) {
            logger.error( "copyRemoteFileToLocal :: Error copying file: {}. Exception: {}", remoteFileName, e.getMessage() );
            return false;
        }
    }




    private ShareHandle ensureShare() {
        ShareHandle handle = shareHolder.get();
        if (handle != null) {
            try {
                if (handle.isUsable()) {
                    return handle;
                }
            } catch (Exception e) {
                closeShareQuietly();
            }
        }

        synchronized (shareLock) {
            // Double-check inside lock
            handle = shareHolder.get();
            if (handle != null && handle.isUsable()) {
                return handle;
            }

            closeShareQuietly();

            SMBClient client = null;
            Connection connection = null;
            Session session = null;
            DiskShare share = null;

            try {
                AuthenticationContext authContext =
                        (StringUtils.isNotBlank(sharedFolderConfig.getUsername())
                                && StringUtils.isNotBlank(sharedFolderConfig.getPassword()))
                                ? new AuthenticationContext(
                                sharedFolderConfig.getUsername(),
                                sharedFolderConfig.getPassword().toCharArray(),
                                "")
                                : AuthenticationContext.anonymous();

                SmbConfig config = SmbConfig.builder()
                        .withTimeout(120, TimeUnit.SECONDS)             // SMB op timeout
                        .withSoTimeout((int) Duration.ofSeconds(300).toMillis()) // socket read timeout
                        .build();

                client = new SMBClient(config);
                connection = client.connect(sharedFolderConfig.getServername());
                session = connection.authenticate(authContext);
                share = (DiskShare) session.connectShare(sharedFolderConfig.getSharepath());

                handle = new ShareHandle(client, connection, session, share);
                shareHolder.set(handle);
                return handle;

            } catch (IOException e) {
                logger.error("ensureShare :: Unable to open SMB share {}", e.getMessage());
                // Close resources created in this attempt (they are not in shareHolder yet)
                closeQuietly(share, session, connection, client);
                return null;
            }
        }
    }

    private void closeQuietly(DiskShare share, Session session, Connection connection, SMBClient client) {
        try {
            if (share != null) share.close();
        } catch (Exception ignored) {  logger.debug("closeQuietly :: share.close() problem   Ignore Exception for {}",ignored.getMessage());}
        try {
            if (session != null) session.logoff();
        } catch (Exception ignored) { logger.debug("closeQuietly :: session.logoff() problem  Ignore Exception for {}",ignored.getMessage()); }
        try {
            if (connection != null) connection.close();
        } catch (Exception ignored) { logger.debug("closeQuietly :: onnection.close() problem Ignore Exception for {}",ignored.getMessage());}
        try {
            if (client != null) client.close();
        } catch (Exception ignored) { logger.debug("closeQuietly :: client.close() problem Ignore Exception for {}",ignored.getMessage()); }
    }

    private void closeShareQuietly ( ) {
        ShareHandle handle = shareHolder.get( );
        if ( handle != null ) {
            try {
                handle.share.close( );
            } catch ( Exception ignored ) {
                logger.debug("closeShareQuietly :: handle.share.close( ) problem Ignore Exception for {}",ignored.getMessage());
            }

            try {
                handle.session.logoff( );
            } catch ( Exception e ) {
                // Broken pipe while logging off is expected if server closed first
                logger.debug( "SMBJ logoff best-effort: {}", e.getMessage() );
            }
            try {
                handle.connection.close( );
            } catch ( Exception e ) {
                logger.debug( "SMBJ connection close best-effort: {}", e.getMessage() );
            }
            try {
                handle.client.close( );
            } catch ( Exception ignored ) {
                logger.debug("closeShareQuietly :: handle.client.close( ) problem Ignore Exception for {}",ignored.getMessage());
            }
            shareHolder.remove( );
        }
    }

    private static final class ShareHandle implements AutoCloseable {
        final SMBClient client;
        final Connection connection;
        final Session session;
        final DiskShare share;

        ShareHandle ( SMBClient client, Connection connection, Session session, DiskShare share ) {
            this.client = client;
            this.connection = connection;
            this.session = session;
            this.share = share;
        }

        boolean isUsable ( ) {
            try {
                return client != null && connection != null && connection.isConnected( ) && session != null && share != null && share.isConnected( );
            } catch ( Exception e ) {
                return false;
            }
        }

        @Override
        public void close ( ) {
            try {
                share.close( );
            } catch ( Exception ignored ) {
                logger.debug("close :: handle.share.close( ) problem Ignore Exception for {}",ignored.getMessage());
            }
            try {
                session.logoff( );
            } catch ( Exception ignored ) {
                logger.debug("close :: session.logoff( ) problem Ignore Exception for {}",ignored.getMessage());
            }
            try {
                connection.close( );
            } catch ( Exception ignored ) {
                logger.debug("close ::  connection.close( ) problem Ignore Exception for {}",ignored.getMessage());
            }
            try {
                client.close( );
            } catch ( Exception ignored ) {
                logger.debug("close ::  client.close( ) problem Ignore Exception for {}",ignored.getMessage());
            }
        }
    }

}
