package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.KafkaTopicConfig;
import com.eh.digitalpathology.dicomreceiver.model.DicomRequestDBObject;
import com.eh.digitalpathology.dicomreceiver.model.ReqGeneratorNotificationMsg;
import com.eh.digitalpathology.dicomreceiver.model.SlideScanProgressEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;

import static com.eh.digitalpathology.dicomreceiver.constants.SlideScanStatusConstant.SLIDE_SCAN_ENRICH_IN_PROGRESS;

@Service
public class FileProcessingService {
    private final DicomExtractorService dicomExtractorService;
    private final DatabaseService databaseService;
    private final EventNotificationService eventNotificationService;
    private final KafkaTopicConfig kafkaTopicConfig;
    public static final String DICOM_RECEIVER = "dicom-receiver";
    private static final Logger log = LoggerFactory.getLogger(FileProcessingService.class.getName());
    ObjectMapper objectMapper = new ObjectMapper();

    public FileProcessingService(DicomExtractorService dicomExtractorService, DatabaseService databaseService, EventNotificationService eventNotificationService, KafkaTopicConfig kafkaTopicConfig) {
        this.dicomExtractorService = dicomExtractorService;
        this.databaseService = databaseService;
        this.eventNotificationService = eventNotificationService;
        this.kafkaTopicConfig = kafkaTopicConfig;
    }

    public void processFile(WatchEvent<?> event, String fileStore, String intermediateStore) {
        log.info("processFile :: ===============================>Process File thread Started:{}", Thread.currentThread().getName());
        Path dir = Paths.get(fileStore);
        Path filePath = dir.resolve((Path) event.context());
        log.info("processFile :: ===============================>\n======>File received by dicom-file-watcher======>\n {}", filePath);
        if (Files.exists(filePath)) {
            log.info("processFile :: File exists & ready to process: {}", filePath.getFileName());
            log.info("processFile :: *********************\n*************\nProcessing file at: {}", filePath);

            if (filePath.getFileName().toString().endsWith(".svs")) {
                log.info("processFile :: SVS file detected, skipping DICOM processing for file: {}", filePath);
                return;
            }
            try {

                DicomRequestDBObject dicomRequestDBObject = dicomExtractorService.extract(intermediateStore, filePath);
                if (dicomRequestDBObject == null) {
                    log.error("processFile :: Dicom extracted for qa slide or dicomdir or failed for file: {}", filePath);
                    return;
                }
                String status = databaseService.insertDicomData(dicomRequestDBObject, DICOM_RECEIVER);
                log.info("processFile :: response from insertDicomData(): {}", status);
                if ("success".equalsIgnoreCase(status)) {
                    log.info("processFile :: Data insertion successful !!!\n sending kafka notification..");
                    notifyKafka(dicomRequestDBObject);
                    SlideScanProgressEvent slideScanProgressEvent = new SlideScanProgressEvent(dicomRequestDBObject.getBarcode(), dicomRequestDBObject.getDeviceSerialNumber(), SLIDE_SCAN_ENRICH_IN_PROGRESS);
                    notifyKafka(kafkaTopicConfig.getScanProgress(), slideScanProgressEvent.slideBarcode(), slideScanProgressEvent);
                } else {
                    log.info("processFile :: KAFKA NOT NOTIFIED!!!.. ");
                }
            } catch (Exception e) {
                log.error("processFile :: Exception occurred while processing file: {}", e.getMessage());
            }
        }
    }

    public void notifyKafka(DicomRequestDBObject requestDBObject) {
        log.info("notifyKafka :: =======>Sending notification to KAFKA: {}", requestDBObject);
        ReqGeneratorNotificationMsg msg = new ReqGeneratorNotificationMsg(requestDBObject.getBarcode(), requestDBObject.getSopInstanceUid(), requestDBObject.getSeriesInstanceUid(), requestDBObject.getDeviceSerialNumber());

        String msgString = null;
        try {
            msgString = objectMapper.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            log.error("notifyKafka :: unable to convert object to string :: {}", e.getMessage());
        }
        eventNotificationService.sendEvent(kafkaTopicConfig.getReceiver(), requestDBObject.getBarcode(), msgString);
    }

    public void notifyKafka(String topic, String key, Object value) {
        try {
            eventNotificationService.sendEvent(topic, key, objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            log.error("notifyKafka: JSON serialization failed | topic={} key={} error={}",
                    topic, key, e.getMessage(), e);
        }
    }


}