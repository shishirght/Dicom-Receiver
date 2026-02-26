package com.eh.digitalpathology.dicomreceiver.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Configuration
public class ExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger( ExecutorConfig.class );

    private ExecutorService directoryWatcherExecutor;
    private ExecutorService remoteDirectoryWatcherExecutor;
    private ScheduledExecutorService remoteDirectoryWatcherScheduledExecutor;
    private ScheduledExecutorService barcodeUploadScheduledExecutor;
    private ScheduledExecutorService stabilizerScheduler;

    @Bean( name = "directoryWatcherExecutor" )
    public ExecutorService directoryWatcherExecutor ( ) {
        this.directoryWatcherExecutor = Executors.newScheduledThreadPool( Runtime.getRuntime( ).availableProcessors( ) );
        return this.directoryWatcherExecutor;
    }
    @Bean( name = "remoteDirectoryWatcherExecutor" )
    public ExecutorService remoteDirectoryWatcherExecutor ( ) {
        this.remoteDirectoryWatcherExecutor = Executors.newScheduledThreadPool( Runtime.getRuntime( ).availableProcessors( ) );
        return this.remoteDirectoryWatcherExecutor;
    }
    @Bean( name = "remoteDirectoryWatcherScheduledExecutor" )
    public ScheduledExecutorService remoteDirectoryWatcherScheduledExecutor ( ) {
        this.remoteDirectoryWatcherScheduledExecutor = Executors.newScheduledThreadPool( Runtime.getRuntime( ).availableProcessors( ) );
        return this.remoteDirectoryWatcherScheduledExecutor;
    }
    @Bean( name = "barcodeUploadScheduledExecutor" )
    public ScheduledExecutorService barcodeUploadScheduledExecutor ( ) {
        this.barcodeUploadScheduledExecutor = Executors.newScheduledThreadPool( Runtime.getRuntime( ).availableProcessors( ) );
        return this.barcodeUploadScheduledExecutor;
    }

    @Bean(name = "stabilizerScheduler")
    public ScheduledExecutorService stabilizerScheduler(){
        this.stabilizerScheduler = Executors.newScheduledThreadPool( Runtime.getRuntime( ).availableProcessors( ) );
        return this.stabilizerScheduler;
    }
    @PreDestroy
    public void shutdownExecutors ( ) {

        shutdownExecutor( directoryWatcherExecutor, "Directory Watcher ExecutorService" );
        shutdownExecutor( remoteDirectoryWatcherExecutor, " Remote Directory Watcher ExecutorService" );
        shutdownExecutor( remoteDirectoryWatcherScheduledExecutor, " Remote Directory Watcher Scheduled ExecutorService" );
        shutdownExecutor( barcodeUploadScheduledExecutor, " Barcode Uploader Scheduled ExecutorService" );
        shutdownExecutor( stabilizerScheduler, " Stabilizer Scheduled ExecutorService" );
    }

    private void shutdownExecutor ( ExecutorService executor, String name ) {
        if ( executor != null ) {
            executor.shutdown( );
            try {
                if ( !executor.awaitTermination( 60, TimeUnit.SECONDS ) ) {
                    executor.shutdownNow( );
                    if ( !executor.awaitTermination( 60, TimeUnit.SECONDS ) ) {
                        log.error( "{} did not terminate", name );
                    }
                }
            } catch ( InterruptedException ex ) {
                executor.shutdownNow( );
                Thread.currentThread( ).interrupt( );
            }
        }
    }
}
