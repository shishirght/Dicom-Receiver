package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.KafkaTopicConfig;
import com.eh.digitalpathology.dicomreceiver.model.DicomDirDocument;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class DicomDirServiceTest {

    @Mock
    private DatabaseService databaseService;

    @Mock
    private EventNotificationService eventNotificationService;

    @Mock
    private KafkaTopicConfig kafkaTopicConfig;

    @InjectMocks
    private DicomDirService dicomDirService;

    @TempDir
    Path tempDir;

    private Path dicomFilePath;

    @BeforeEach
    void setUp() throws Exception {
        dicomFilePath = tempDir.resolve("testDicomDir.dcm");
        Files.writeString(dicomFilePath, "test dicom content");
    }

    @Test
    void testFetchAndStoreMetaData_Success() throws Exception {
        DicomDirDocument doc = new DicomDirDocument("studyId","seriesId",3,new byte[3]);
        when(kafkaTopicConfig.getStgcmt()).thenReturn("test-topic");
        when(databaseService.saveMetaDataInfo(any())).thenReturn("success");

        dicomDirService.fetchAndStoreMetaData(dicomFilePath, doc);

        verify(databaseService, times(1)).saveMetaDataInfo(any(DicomDirDocument.class));
        verify(eventNotificationService, times(1))
                .sendEvent(eq("test-topic"), eq(""), anyString());
        assertFalse(Files.exists(dicomFilePath));
    }

    @Test
    void testFetchMetaData_WithStudySeriesAndImages_ShouldReturnDocument()  {

        Attributes dataSet = new Attributes();
        Attributes studyRecord = new Attributes();
        studyRecord.setString(Tag.DirectoryRecordType, VR.CS, "STUDY");
        studyRecord.setString(Tag.StudyInstanceUID, VR.UI, "STUDY123");

        Attributes seriesRecord = new Attributes();
        seriesRecord.setString(Tag.DirectoryRecordType, VR.CS, "SERIES");
        seriesRecord.setString(Tag.SeriesInstanceUID, VR.UI, "SERIES123");

        Attributes imageRecord = new Attributes();
        imageRecord.setString(Tag.DirectoryRecordType, VR.CS, "IMAGE");

        Sequence sequence = dataSet.newSequence(Tag.DirectoryRecordSequence, 3);
        sequence.add(studyRecord);
        sequence.add(seriesRecord);
        sequence.add(imageRecord);

        DicomDirDocument result = dicomDirService.fetchMetaData(dicomFilePath, dataSet);

        assertNotNull(result);
        assertEquals("STUDY123", result.studyId());
        assertEquals("SERIES123", result.seriesId());
        assertEquals(1, result.imageCount());

    }


    @Test
    void testFetchMetaData_WithNullSequence_ShouldReturnNull() {
        Attributes dataSet = new Attributes();
        DicomDirDocument result = dicomDirService.fetchMetaData(dicomFilePath, dataSet);
        assertNull(result);
    }


    @Test
    void testFetchMetaData_WhenFileReadFails_ShouldReturnNull() throws Exception {
        Attributes dataSet = new Attributes();
        Sequence sequence = dataSet.newSequence(Tag.DirectoryRecordSequence, 1);
        Attributes rec = new Attributes();
        rec.setString(Tag.DirectoryRecordType, VR.CS, "STUDY");
        rec.setString(Tag.StudyInstanceUID, VR.UI, "STUDY001");
        sequence.add(rec);

        Path unreadableFile = tempDir.resolve("deletedFile.dcm");
        Files.writeString(unreadableFile, "temporary content");
        Files.delete(unreadableFile);

        DicomDirDocument result = dicomDirService.fetchMetaData(unreadableFile, dataSet);

        assertNull(result);
    }
    // -----------------------------------------------------------------------
    // ADDITIONAL TEST 1:
    // Covers: catch (IOException | DbConnectorExeption e) in fetchAndStoreMetaData
    // Triggered when databaseService.saveMetaDataInfo() throws DbConnectorExeption
    // -----------------------------------------------------------------------
    @Test
    void testFetchAndStoreMetaData_WhenDbThrowsDbConnectorException_ShouldCatchAndNotRethrow() throws Exception {
        DicomDirDocument doc = new DicomDirDocument("studyId", "seriesId", 3, new byte[3]);

        when(databaseService.saveMetaDataInfo(any()))
                .thenThrow(new com.eh.digitalpathology.dicomreceiver.exceptions.DbConnectorExeption("ERR", "DB error"));

        // Must NOT propagate — exception is caught and logged internally
        assertDoesNotThrow(() -> dicomDirService.fetchAndStoreMetaData(dicomFilePath, doc));

        // Kafka must NOT be called since save failed
        verify(eventNotificationService, never()).sendEvent(any(), any(), any());

        // Finally block must still delete the file
        assertFalse(Files.exists(dicomFilePath));
    }

    @Test
    void testFetchAndStoreMetaData_WhenObjectMapperThrowsIOException_ShouldCatchAndNotRethrow() throws Exception {
        // Arrange: inject a mock ObjectMapper that throws JsonProcessingException
        ObjectMapper mockObjectMapper = mock(ObjectMapper.class);
        when(mockObjectMapper.writeValueAsString(any()))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Serialization failed") {});

        // Inject mock objectMapper into the service via reflection
        java.lang.reflect.Field field = DicomDirService.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(dicomDirService, mockObjectMapper);

        DicomDirDocument doc = new DicomDirDocument("studyId", "seriesId", 3, new byte[3]);
        when(kafkaTopicConfig.getStgcmt()).thenReturn("test-topic");
        when(databaseService.saveMetaDataInfo(any())).thenReturn("success");
        // status == "success" → enters the if → calls objectMapper.writeValueAsString() → throws IOException
        // → caught by catch (IOException | DbConnectorExeption e)

        assertDoesNotThrow(() -> dicomDirService.fetchAndStoreMetaData(dicomFilePath, doc));

        // sendEvent must NOT be called — exception thrown before it
        verify(eventNotificationService, never()).sendEvent(any(), any(), any());

        // Finally still deletes the file
        assertFalse(Files.exists(dicomFilePath));
    }
    // -----------------------------------------------------------------------
    // ADDITIONAL TEST 3:
    // Covers: finally inner catch (IOException e) — when Files.deleteIfExists fails
    // We use a spy on the service and MockedStatic on Files to force the IOException
    // -----------------------------------------------------------------------
    @Test
    void testFetchAndStoreMetaData_WhenDeleteFileFails_ShouldLogAndNotRethrow() throws Exception {
        DicomDirDocument doc = new DicomDirDocument("studyId", "seriesId", 3, new byte[3]);

        when(kafkaTopicConfig.getStgcmt()).thenReturn("test-topic");
        when(databaseService.saveMetaDataInfo(any())).thenReturn("success");

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            // Allow writeValueAsString to work — only deleteIfExists should fail
            mockedFiles.when(() -> Files.deleteIfExists(any(Path.class)))
                    .thenThrow(new IOException("Permission denied"));

            // Must NOT propagate — inner catch handles it
            assertDoesNotThrow(() -> dicomDirService.fetchAndStoreMetaData(dicomFilePath, doc));

            verify(eventNotificationService).sendEvent(any(), any(), any());
        }
    }

    // -----------------------------------------------------------------------
    // ADDITIONAL TEST 4:
    // Covers: if (dicomDirDocument != null) → false branch (null passed in)
    // Only the finally block runs — file is deleted, nothing else happens
    // -----------------------------------------------------------------------
    @Test
    void testFetchAndStoreMetaData_WhenDocumentIsNull_ShouldSkipSaveAndDeleteFile() throws Exception {
        // Pass null → if (dicomDirDocument != null) is false → skip entire try body
        assertDoesNotThrow(() -> dicomDirService.fetchAndStoreMetaData(dicomFilePath, null));

        // DB and Kafka must never be called
        verify(databaseService, never()).saveMetaDataInfo(any());
        verify(eventNotificationService, never()).sendEvent(any(), any(), any());

        // Finally block still deletes the file
        assertFalse(Files.exists(dicomFilePath));
    }

    // -----------------------------------------------------------------------
    // ADDITIONAL TEST 5:
    // Covers: status != "success" branch in fetchAndStoreMetaData
    // DB returns a non-success status → Kafka event is NOT sent
    // -----------------------------------------------------------------------
    @Test
    void testFetchAndStoreMetaData_WhenStatusIsNotSuccess_ShouldNotSendKafkaEvent() throws Exception {
        DicomDirDocument doc = new DicomDirDocument("studyId", "seriesId", 3, new byte[3]);

        when(databaseService.saveMetaDataInfo(any())).thenReturn("failure");

        assertDoesNotThrow(() -> dicomDirService.fetchAndStoreMetaData(dicomFilePath, doc));

        // Kafka must NOT be notified when status != "success"
        verify(eventNotificationService, never()).sendEvent(any(), any(), any());

        // File still deleted in finally
        assertFalse(Files.exists(dicomFilePath));
    }

    // -----------------------------------------------------------------------
    // ADDITIONAL TEST 6:
    // Covers: fetchMetaData — only STUDY records present (no SERIES, no IMAGE)
    // seriesId stays null, imageCount stays 0
    // -----------------------------------------------------------------------
    @Test
    void testFetchMetaData_WithOnlyStudyRecord_ShouldReturnDocumentWithNullSeriesAndZeroImages() {
        Attributes dataSet = new Attributes();
        Sequence sequence = dataSet.newSequence(Tag.DirectoryRecordSequence, 1);

        Attributes studyRecord = new Attributes();
        studyRecord.setString(Tag.DirectoryRecordType, VR.CS, "STUDY");
        studyRecord.setString(Tag.StudyInstanceUID, VR.UI, "STUDY_ONLY");
        sequence.add(studyRecord);

        DicomDirDocument result = dicomDirService.fetchMetaData(dicomFilePath, dataSet);

        assertNotNull(result);
        assertEquals("STUDY_ONLY", result.studyId());
        assertNull(result.seriesId());
        assertEquals(0, result.imageCount());
    }

    // -----------------------------------------------------------------------
    // ADDITIONAL TEST 7:
    // Covers: fetchMetaData — multiple IMAGE records → imageCount increments correctly
    // Also verifies second STUDY/SERIES records are skipped (studyId/seriesId already set)
    // -----------------------------------------------------------------------
    @Test
    void testFetchMetaData_WithMultipleImages_ShouldCountAllImages() {
        Attributes dataSet = new Attributes();
        Sequence sequence = dataSet.newSequence(Tag.DirectoryRecordSequence, 5);

        Attributes study = new Attributes();
        study.setString(Tag.DirectoryRecordType, VR.CS, "STUDY");
        study.setString(Tag.StudyInstanceUID, VR.UI, "STUDY001");
        sequence.add(study);

        // Second STUDY — should be ignored (studyId already set)
        Attributes study2 = new Attributes();
        study2.setString(Tag.DirectoryRecordType, VR.CS, "STUDY");
        study2.setString(Tag.StudyInstanceUID, VR.UI, "STUDY002");
        sequence.add(study2);

        Attributes series = new Attributes();
        series.setString(Tag.DirectoryRecordType, VR.CS, "SERIES");
        series.setString(Tag.SeriesInstanceUID, VR.UI, "SERIES001");
        sequence.add(series);

        // Three IMAGE records
        for (int i = 0; i < 3; i++) {
            Attributes image = new Attributes();
            image.setString(Tag.DirectoryRecordType, VR.CS, "IMAGE");
            sequence.add(image);
        }

        DicomDirDocument result = dicomDirService.fetchMetaData(dicomFilePath, dataSet);

        assertNotNull(result);
        assertEquals("STUDY001", result.studyId());   // first STUDY wins
        assertEquals("SERIES001", result.seriesId());
        assertEquals(3, result.imageCount());
    }

}








