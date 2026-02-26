package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.KafkaTopicConfig;
import com.eh.digitalpathology.dicomreceiver.model.PathQa;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class BarcodeUploadTrackerService {
    private static final Logger log = LoggerFactory.getLogger( BarcodeUploadTrackerService.class.getName( ) );

    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ScheduledExecutorService scheduler;
    private final EventNotificationService eventNotificationService;
    private static final int QUIET_PERIOD_MINUTES = 5; // Time to wait before sending notification
    private final KafkaTopicConfig kafkaTopicConfig;

    public BarcodeUploadTrackerService ( @Qualifier("barcodeUploadScheduledExecutor") ScheduledExecutorService scheduler, EventNotificationService eventNotificationService, KafkaTopicConfig kafkaTopicConfig ) {
        this.scheduler = scheduler;
        this.eventNotificationService = eventNotificationService;
        this.kafkaTopicConfig = kafkaTopicConfig;
    }


    public void recordUpload(String barcodeValue, String studyInstanceUID, String seriesInstanceUID, String deviceSerialNumber) {
        log.info("recordUpload :: Tracked upload for barcode {}", barcodeValue);

        // Cancel any existing scheduled task for this barcode
        ScheduledFuture<?> existingTask = scheduledTasks.get(seriesInstanceUID);
        if (existingTask != null && !existingTask.isDone()) {
            existingTask.cancel(false);
            log.info("recordUpload :: Resetting timer for barcode {}", seriesInstanceUID);
        }

        // Schedule a new task to run after quietPeriodMinutes
        ScheduledFuture<?> newTask = scheduler.schedule(() -> {
            sendKafkaNotification(barcodeValue, studyInstanceUID, seriesInstanceUID, deviceSerialNumber);
            scheduledTasks.remove(seriesInstanceUID); // Clean up after sending
        }, QUIET_PERIOD_MINUTES, TimeUnit.MINUTES);

        scheduledTasks.put(seriesInstanceUID, newTask);
    }

    private void sendKafkaNotification(String barcode, String studyInstanceUID, String seriesInstanceUID, String deviceSerialNumber) {
        try {
            PathQa pathQa = new PathQa(barcode, studyInstanceUID, seriesInstanceUID, deviceSerialNumber);
            String payload = objectMapper.writeValueAsString(pathQa);
            eventNotificationService.sendEvent( kafkaTopicConfig.getPathqa( ), barcode, payload);
            log.info("sendKafkaNotification :: Kafka notification sent for barcode: {}", barcode);
        } catch (Exception e) {
            log.error("Failed to send Kafka notification for barcode {}: {}", barcode, e.getMessage());
        }
    }
}

