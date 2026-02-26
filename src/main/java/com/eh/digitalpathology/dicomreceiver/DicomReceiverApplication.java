package com.eh.digitalpathology.dicomreceiver;


import com.eh.digitalpathology.dicomreceiver.service.DirectoryWatcher;
import com.eh.digitalpathology.dicomreceiver.service.RemoteDirectoryWatcher;
import com.eh.digitalpathology.dicomreceiver.util.CommonUtils;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class DicomReceiverApplication implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DicomReceiverApplication.class.getName());

    private final DirectoryWatcher directoryWatcher;
    private final CommonUtils commonUtils;


    private RemoteDirectoryWatcher remoteDirectoryWatcher;

    @Autowired
    public DicomReceiverApplication( DirectoryWatcher directoryWatcher, CommonUtils commonUtils, RemoteDirectoryWatcher remoteDirectoryWatcher) {

        this.directoryWatcher = directoryWatcher;
        this.commonUtils = commonUtils;
        this.remoteDirectoryWatcher = remoteDirectoryWatcher;

    }

    public static void main(String[] args) {
        SpringApplication.run(DicomReceiverApplication.class, args);
    }
    private  ExecutorService executorService = Executors.newFixedThreadPool(4);

    @Override
    public void run(String... args) throws Exception {
        // Adjust the number of threads as needed
        // Get the root folder
        String receivedFiles = commonUtils.getLocalStoragePath();
        log.info("run :: receivedFiles :: {}", receivedFiles);
        String intermediateFileStorage = commonUtils.getIntermediateFileServer( );
        log.info("run :: intermediateFileStorage :: {}", intermediateFileStorage);

        executorService.submit(() -> {
            log.info("run :: ===========>Starting local directory watcher service...");
            directoryWatcher.directoryLookup(receivedFiles, intermediateFileStorage);
            log.info("run :: Directory watcher service completed.");
        });
        boolean enableRemoteDirectoryWatcher = commonUtils.isEnableRemoteDirectoryWatcher();
        log.info("run :: enableRemoteDirectoryWatcher: {}", enableRemoteDirectoryWatcher);
        if(enableRemoteDirectoryWatcher) {
            executorService.submit(() -> {
                log.info("run :: ============>Starting remote directory watcher service...");
                try {
                    remoteDirectoryWatcher.watchSharedDirectory();
                    log.info("run :: Remote directory watcher service completed.");
                } catch (Exception e) {
                    log.error("run :: Error running remote directory watcher: {} ", e.getMessage());
                }
            });
        }else{
            log.info("run :: ===============> Disabled remote directory watcher service...");
        }
    }

    @PreDestroy
    public void shutdown ( ) {
        executorService.shutdown( );
        try {
            if ( !executorService.awaitTermination( 60, TimeUnit.SECONDS ) ) {
                executorService.shutdownNow( );
                if ( !executorService.awaitTermination( 60, TimeUnit.SECONDS ) ) {
                    log.error( "ExecutorService did not terminate" );
                }
            }
        } catch ( InterruptedException ex ) {
            executorService.shutdownNow( );
            Thread.currentThread( ).interrupt( );
        }
    }
}

