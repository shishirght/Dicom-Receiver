package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.KafkaTopicConfig;
import com.eh.digitalpathology.dicomreceiver.model.DicomRequestDBObject;
import com.eh.digitalpathology.dicomreceiver.model.ReqGeneratorNotificationMsg;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileProcessingServiceTest {

    @Mock
    private DicomExtractorService dicomExtractorService;

    @Mock
    private DatabaseService databaseService;

    @Mock
    private EventNotificationService eventNotificationService;

    @Mock
    private KafkaTopicConfig kafkaTopicConfig;

    @InjectMocks
    private FileProcessingService fileProcessingService;

    private static final String INTERMEDIATE = "intermediate";
    private static final Path TEMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"));

    @Test
    void testProcessFile_FileDoesNotExist_shouldSkipProcessing() {
        WatchEvent<Path> mockEvent = mock(WatchEvent.class);
        when(mockEvent.context()).thenReturn(Paths.get("missing.dcm"));
        Path mockFile = TEMP_DIR.resolve("missing.dcm");

        try (var filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(mockFile)).thenReturn(false);

            fileProcessingService.processFile(mockEvent, TEMP_DIR.toString(), INTERMEDIATE);

            verify(dicomExtractorService, never()).extract(any(), any());
            verify(databaseService, never()).insertDicomData(any(), any());
            verify(eventNotificationService, never()).sendEvent(any(), any(), any());
        }
    }

    @Test
    void testProcessFile_SvsFile_shouldSkipProcessing() {
        WatchEvent<Path> mockEvent = mock(WatchEvent.class);
        when(mockEvent.context()).thenReturn(Paths.get("slide.svs"));
        Path mockFile = TEMP_DIR.resolve("slide.svs");

        try (var filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(mockFile)).thenReturn(true);

            fileProcessingService.processFile(mockEvent, TEMP_DIR.toString(), INTERMEDIATE);

            verify(dicomExtractorService, never()).extract(any(), any());
            verify(databaseService, never()).insertDicomData(any(), any());
            verify(eventNotificationService, never()).sendEvent(any(), any(), any());
        }
    }

    @Test
    void testProcessFile_forDicomDirFile_shouldSkipProcessing() {
        WatchEvent<Path> mockEvent = mock(WatchEvent.class);
        when(mockEvent.context()).thenReturn(Paths.get("DICOMDIR"));
        Path mockFile = TEMP_DIR.resolve("DICOMDIR");

        try (var filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(mockFile)).thenReturn(true);

            when(dicomExtractorService.extract(INTERMEDIATE, mockFile)).thenReturn(null);

            fileProcessingService.processFile(mockEvent, TEMP_DIR.toString(), INTERMEDIATE);

            verify(dicomExtractorService, times(1)).extract(INTERMEDIATE, mockFile);
            verify(databaseService, never()).insertDicomData(any(), any());
            verify(eventNotificationService, never()).sendEvent(any(), any(), any());
        }
    }

    @Test
    void testProcessFile_SuccessFlow_shouldNotifyKafkaTwice() {
        WatchEvent<Path> mockEvent = mock(WatchEvent.class);
        when(mockEvent.context()).thenReturn(Paths.get("sample.dcm"));
        Path mockFile = TEMP_DIR.resolve("sample.dcm");

        DicomRequestDBObject mockDBObject = new DicomRequestDBObject();
        mockDBObject.setBarcode("B123");
        mockDBObject.setSopInstanceUid("SOP123");
        mockDBObject.setSeriesInstanceUid("SERIES123");
        mockDBObject.setDeviceSerialNumber("DEVICE001");

        when(kafkaTopicConfig.getReceiver()).thenReturn("receiver-topic");
        when(kafkaTopicConfig.getScanProgress()).thenReturn("scan-progress-topic");

        try (var filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(mockFile)).thenReturn(true);

            when(dicomExtractorService.extract(INTERMEDIATE, mockFile)).thenReturn(mockDBObject);
            when(databaseService.insertDicomData(mockDBObject, FileProcessingService.DICOM_RECEIVER))
                    .thenReturn("success");

            fileProcessingService.processFile(mockEvent, TEMP_DIR.toString(), INTERMEDIATE);

            verify(dicomExtractorService, times(1)).extract(INTERMEDIATE, mockFile);
            verify(databaseService, times(1)).insertDicomData(mockDBObject, FileProcessingService.DICOM_RECEIVER);
            verify(eventNotificationService, times(1))
                    .sendEvent(eq("receiver-topic"), eq("B123"), anyString());
            verify(eventNotificationService, times(1))
                    .sendEvent(eq("scan-progress-topic"), eq("B123"), anyString());
        }
    }

    @Test
    void testProcessFile_DatabaseInsertFails_shouldNotNotifyKafka() {
        WatchEvent<Path> mockEvent = mock(WatchEvent.class);
        when(mockEvent.context()).thenReturn(Paths.get("sample.dcm"));
        Path mockFile = TEMP_DIR.resolve("sample.dcm");

        DicomRequestDBObject mockDBObject = new DicomRequestDBObject();
        mockDBObject.setBarcode("B123");
        mockDBObject.setSopInstanceUid("SOP123");
        mockDBObject.setSeriesInstanceUid("SERIES123");
        mockDBObject.setDeviceSerialNumber("DEVICE001");

        try (var filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(mockFile)).thenReturn(true);

            when(dicomExtractorService.extract(INTERMEDIATE, mockFile)).thenReturn(mockDBObject);
            when(databaseService.insertDicomData(mockDBObject, FileProcessingService.DICOM_RECEIVER))
                    .thenReturn("failure");

            fileProcessingService.processFile(mockEvent, TEMP_DIR.toString(), INTERMEDIATE);

            verify(databaseService, times(1)).insertDicomData(mockDBObject, FileProcessingService.DICOM_RECEIVER);
            verify(eventNotificationService, never()).sendEvent(any(), any(), any());
        }
    }

    @Test
    void testProcessFile_ExceptionDuringExtraction_shouldBeHandledGracefully() {
        WatchEvent<Path> mockEvent = mock(WatchEvent.class);
        when(mockEvent.context()).thenReturn(Paths.get("sample.dcm"));
        Path mockFile = TEMP_DIR.resolve("sample.dcm");

        try (var filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(mockFile)).thenReturn(true);

            when(dicomExtractorService.extract(INTERMEDIATE, mockFile))
                    .thenThrow(new RuntimeException("Extraction failed"));

            fileProcessingService.processFile(mockEvent, TEMP_DIR.toString(), INTERMEDIATE);

            verify(databaseService, never()).insertDicomData(any(), any());
            verify(eventNotificationService, never()).sendEvent(any(), any(), any());
        }
    }

    @Test
    void testNotifyKafka_withDBObject_sendsEventToKafka() {
        DicomRequestDBObject mockDBObject = new DicomRequestDBObject();
        mockDBObject.setBarcode("B001");
        mockDBObject.setSopInstanceUid("SOP123");
        mockDBObject.setSeriesInstanceUid("SERIES123");
        mockDBObject.setDeviceSerialNumber("DEV001");

        when(kafkaTopicConfig.getReceiver()).thenReturn("receiver-topic");

        fileProcessingService.notifyKafka(mockDBObject);

        verify(eventNotificationService, times(1))
                .sendEvent(eq("receiver-topic"), eq("B001"), anyString());
    }

    @Test
    @Disabled("TBD: err while handling exception")
    void testNotifyKafka_withTopicKeyValue_sendsEventToKafka() throws Exception {
        Object payload = new Object();

        fileProcessingService.notifyKafka("my-topic", "my-key", payload);

        verify(eventNotificationService, times(1))
                .sendEvent(eq("my-topic"), eq("my-key"), anyString());
    }

    @Test
    @Disabled("ERR: only handling exception")
    void testNotifyKafka_withDBObject_handlesJsonProcessingException() {
        DicomRequestDBObject mockDBObject = new DicomRequestDBObject();
        mockDBObject.setBarcode("B001");
        mockDBObject.setSopInstanceUid("SOP123");
        mockDBObject.setSeriesInstanceUid("SERIES123");
        mockDBObject.setDeviceSerialNumber("DEV001");

        when(kafkaTopicConfig.getReceiver()).thenReturn("receiver-topic");

        try (MockedConstruction<ObjectMapper> mapperMock =
                     mockConstruction(ObjectMapper.class, (mock, ctx) ->
                             when(mock.writeValueAsString(any(ReqGeneratorNotificationMsg.class)))
                                     .thenThrow(new JsonProcessingException("JSON error") {}))) {

            fileProcessingService.notifyKafka(mockDBObject);

            verify(eventNotificationService, never()).sendEvent(any(), any(), any());
        }
    }
}