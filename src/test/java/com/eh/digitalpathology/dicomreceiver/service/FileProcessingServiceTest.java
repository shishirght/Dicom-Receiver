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

    @Mock
    private DicomDirService dicomDirService;

    @InjectMocks
    private FileProcessingService fileProcessingService;


    @Test
    void testProcessFile_forDicomDirFile_shouldSkipProcessing() {
        WatchEvent<Path> mockEvent = mock(WatchEvent.class);
        when(mockEvent.context()).thenReturn(Paths.get("DICOMDIR"));

        Path mockDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Path mockFile = mockDir.resolve("DICOMDIR");

        try (var filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(mockFile)).thenReturn(true);

            when(dicomExtractorService.extract(anyString(), eq(mockFile))).thenReturn(null);

            fileProcessingService.processFile(mockEvent, mockDir.toString(), "intermediate");

            verify(dicomExtractorService, times(1)).extract("intermediate", mockFile);
            verify(databaseService, never()).insertDicomData(any(), any());
            verify(eventNotificationService, never()).sendEvent(any(), any(), any());
        }
    }



    @Test
    void testProcessFile_SuccessFlow() {
        WatchEvent<Path> mockEvent = mock(WatchEvent.class);
        when(mockEvent.context()).thenReturn(Paths.get("sample.dcm"));
        Path mockDir = Paths.get(System.getProperty("java.io.tmpdir"));
        Path mockFile = mockDir.resolve("sample.dcm");

        DicomRequestDBObject mockDBObject = new DicomRequestDBObject();
        mockDBObject.setBarcode("B123");
        mockDBObject.setSopInstanceUid("SOP123");
        mockDBObject.setSeriesInstanceUid("SERIES123");
        mockDBObject.setDeviceSerialNumber("DEVICE001");

        when(kafkaTopicConfig.getReceiver()).thenReturn("receiver-topic");

        try (var filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.exists(mockFile)).thenReturn(true);

            when(dicomExtractorService.extract("intermediate", mockFile)).thenReturn(mockDBObject);
            when(databaseService.insertDicomData(mockDBObject, FileProcessingService.DICOM_RECEIVER))
                    .thenReturn("success");

            fileProcessingService.processFile(mockEvent, mockDir.toString(), "intermediate");

            verify(dicomExtractorService, times(1)).extract("intermediate", mockFile);
            verify(databaseService, times(1)).insertDicomData(mockDBObject, FileProcessingService.DICOM_RECEIVER);
            verify(eventNotificationService, times(1))
                    .sendEvent(eq("receiver-topic"), eq("B123"), anyString());
        }
    }

    @Test
    void testNotifyKafka_SendsEventToKafka() {
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

    @Disabled
    @Test
    void testNotifyKafka_HandlesJsonProcessingException() {
        DicomRequestDBObject mockDBObject = new DicomRequestDBObject();
        mockDBObject.setBarcode("B001");
        mockDBObject.setSopInstanceUid("SOP123");
        mockDBObject.setSeriesInstanceUid("SERIES123");
        when(kafkaTopicConfig.getReceiver()).thenReturn("receiver-topic");

        try (MockedConstruction<ObjectMapper> mapperMock =
                     mockConstruction(ObjectMapper.class, (mock, ctx) ->
                             when(mock.writeValueAsString(any(ReqGeneratorNotificationMsg.class)))
                                     .thenThrow(new JsonProcessingException("JSON error") {}))) {

            fileProcessingService.notifyKafka(mockDBObject);

            verify(eventNotificationService, times(1))
                    .sendEvent(eq("receiver-topic"), eq("B001"), isNull());
        }
    }

}