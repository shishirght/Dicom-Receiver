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

import org.mockito.junit.jupiter.MockitoExtension;
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


}








