package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.GcpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class SeriesUploadTrackerService {
    private static final Logger log = LoggerFactory.getLogger( SeriesUploadTrackerService.class.getName( ) );
    private final ConcurrentHashMap<String, ScheduledFuture<?> > scheduledTasks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler;
    private final GcpConfig gcpConfig;
    private final SeriesNotifierService seriesNotifierService;
    private static final int QUIET_PERIOD_MINUTES = 3; // Time to wait before sending notification

    public SeriesUploadTrackerService ( @Qualifier("barcodeUploadScheduledExecutor") ScheduledExecutorService scheduler, GcpConfig gcpConfig, SeriesNotifierService seriesNotifierService ) {
        this.scheduler = scheduler;
        this.gcpConfig = gcpConfig;
        this.seriesNotifierService = seriesNotifierService;
    }

    public void recordUpload(String studyInstanceUID, String seriesInstanceUID) {
        log.info("recordUpload :: Tracked upload for barcode {}", seriesInstanceUID);

        // Cancel any existing scheduled task for this barcode
        ScheduledFuture<?> existingTask = scheduledTasks.get(seriesInstanceUID);
        if (existingTask != null && !existingTask.isDone()) {
            existingTask.cancel(false);
            log.info("recordUpload :: Resetting timer for barcode {}", seriesInstanceUID);
        }

        // Schedule a new task to run after quietPeriodMinutes
        ScheduledFuture<?> newTask = scheduler.schedule(() -> {
            String seriesUrl = String.format( "%s/%s/dicomWeb/studies/%s/series/%s", gcpConfig.getDicomWebUrl( ), gcpConfig.getResearchStoreUrl(), studyInstanceUID, seriesInstanceUID );
            log.info( "recordUpload :: seriesUrl :: {}", seriesUrl );
            seriesNotifierService.notifyVisiopharm( seriesUrl );
            scheduledTasks.remove(seriesInstanceUID); // Clean up after sending
        }, QUIET_PERIOD_MINUTES, TimeUnit.MINUTES);

        scheduledTasks.put(seriesInstanceUID, newTask);
    }
}
