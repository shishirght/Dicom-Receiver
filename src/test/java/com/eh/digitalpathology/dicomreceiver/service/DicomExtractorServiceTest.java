package com.eh.digitalpathology.dicomreceiver.service;


import com.eh.digitalpathology.dicomreceiver.api.DicomHealthcareApiClient;
import com.eh.digitalpathology.dicomreceiver.config.GcpConfig;
import com.eh.digitalpathology.dicomreceiver.config.KafkaTopicConfig;
import com.eh.digitalpathology.dicomreceiver.exceptions.DicomAttributesException;
import com.eh.digitalpathology.dicomreceiver.model.DicomDirDocument;
import com.eh.digitalpathology.dicomreceiver.model.DicomRequestDBObject;
import com.eh.digitalpathology.dicomreceiver.model.SlideScanner;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class DicomExtractorServiceTest  {

    @Mock
    private EventNotificationService eventNotificationService;
    @Mock
    private KafkaTopicConfig kafkaTopicConfig;
    @Mock
    private DatabaseService dbService;
    @Mock
    private DicomHealthcareApiClient dicomHealthcareApiClient;
    @Mock
    private Cache<String, String> studyBarcodeCache;
    @Mock
    private BarcodeUploadTrackerService barcodeUploadTrackerService;
    @Mock
    private DicomDirService dicomDirService;
    @Mock
    private DicomDirDocument dicomDirDocument;

    @Mock
    private GcpConfig gcpConfig;

    @InjectMocks
    private DicomExtractorService dicomExtractorService;

    private Path mockPath;
    private Attributes attributes;


    @BeforeEach
    void setup() {
        mockPath = Paths.get("/mock/path");
        attributes = new Attributes();
        lenient().when(dbService.fetchScannerByDeviceSerialNumber( anyString() )).thenReturn( new SlideScanner( "123", "test", "test", "test", "test", "test", "test", "test", "device123", false, true ) );
        ReflectionTestUtils.setField(dicomExtractorService, "enableBarcodeGeneration", false);
    }

   // @Test
    void testExtract_SuccessfulExtraction() throws Exception {
        Path tempFile = Files.createTempFile("dicom", ".dcm");
        File dicomFile = tempFile.toFile();

        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPInstanceUID, VR.UI, "SOP123");
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, "SERIES123");
        attrs.setString(Tag.StudyInstanceUID, VR.UI, "STUDY123");
        attrs.setString(Tag.BarcodeValue, VR.LO, "BC-1234");
        attrs.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");

        try (DicomOutputStream dos = new DicomOutputStream(dicomFile)) {
            dos.writeDataset(attrs.createFileMetaInformation("1.2.840.10008.1.2.1"), attrs);
        }
        try (MockedStatic<StorageOptions> mockedStorageOptions = mockStatic(StorageOptions.class)) {
            StorageOptions.Builder mockBuilder = mock(StorageOptions.Builder.class);
            StorageOptions mockOptions = mock(StorageOptions.class);
            Storage mockStorage = mock(Storage.class);
            WriteChannel mockWriter = mock(WriteChannel.class);

            mockedStorageOptions.when(StorageOptions::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.setRetrySettings(any())).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockOptions);
            when(mockOptions.getService()).thenReturn(mockStorage);
            when(mockStorage.writer(any(BlobInfo.class))).thenReturn(mockWriter);
            DicomRequestDBObject result = dicomExtractorService.extract("bucket-test", Path.of(dicomFile.getAbsolutePath()));

            assertNotNull(result);
            assertEquals("SOP123", result.getSopInstanceUid());
            assertEquals("SERIES123", result.getSeriesInstanceUid());
            assertNull( result.getEnrichmentTimestamp( ) );
            assertNull( result.getActualStudyInstanceUid( ) );
            assertEquals("STUDY123", result.getOriginalStudyInstanceUid());
            assertTrue(result.getBarcode().startsWith("BC-"));
            assertTrue(result.getIntermediateStoragePath().contains("gs://bucket-test"));
            assertNotNull(result.getDicomInstanceReceivedTimestamp());
        }
    }

   // @Test
    void testExtract_BarcodeGeneratedWhenMissing() throws Exception {

        Path tempFile = Files.createTempFile("dicom-autogen-barcode", ".dcm");
        File dicomFile = tempFile.toFile();

        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPInstanceUID, VR.UI, "SOP123");
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, "SERIES123");
        attrs.setString(Tag.StudyInstanceUID, VR.UI, "STUDY123");
        attrs.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");

        try (DicomOutputStream dos = new DicomOutputStream(dicomFile)) {
            dos.writeDataset(attrs.createFileMetaInformation("1.2.840.10008.1.2.1"), attrs);
        }

        ReflectionTestUtils.setField(dicomExtractorService, "enableBarcodeGeneration", true);
        try (MockedStatic<StorageOptions> mockedStorageOptions = mockStatic(StorageOptions.class)) {
            StorageOptions.Builder mockBuilder = mock(StorageOptions.Builder.class);
            StorageOptions mockOptions = mock(StorageOptions.class);
            Storage mockStorage = mock(Storage.class);
            WriteChannel mockWriter = mock(WriteChannel.class);


            mockedStorageOptions.when(StorageOptions::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.setRetrySettings(any())).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockOptions);
            when(mockOptions.getService()).thenReturn(mockStorage);
            when(mockStorage.writer(any(BlobInfo.class))).thenReturn(mockWriter);
            DicomRequestDBObject result = dicomExtractorService.extract("autogen-bucket", Path.of(dicomFile.getAbsolutePath()));

            assertNotNull(result);
            assertEquals("SOP123", result.getSopInstanceUid());
            assertEquals("SERIES123", result.getSeriesInstanceUid());
            assertNotNull(result.getBarcode());
            assertTrue(result.getBarcode().startsWith("BC-"));
            assertTrue(result.getIntermediateStoragePath().contains("gs://autogen-bucket"));
        }
    }

    @Test
    void testExtract_WhenBarcodeAlreadyExists_ShouldStoreAndRecordUpload() throws Exception {

        Attributes mockAttributes = mock(Attributes.class);
        Attributes mockFmi = mock(Attributes.class);

        when(mockAttributes.getString(Tag.BarcodeValue)).thenReturn("B123");
        when(mockAttributes.getString(Tag.SeriesInstanceUID)).thenReturn("SERIES123");
        when(mockAttributes.getString(Tag.StudyInstanceUID)).thenReturn("STUDY123");
        when(mockAttributes.getString(Tag.DeviceSerialNumber)).thenReturn("DEVICE456");
        when(mockAttributes.getString(Tag.SOPClassUID)).thenReturn("SOP001");
        when(mockAttributes.getString(Tag.SOPInstanceUID)).thenReturn("SOPI001");
        when(dbService.isBarcodeExists("B123")).thenReturn(true);
        when(gcpConfig.getPathqaStoreUrl()).thenReturn( "test" );


        try (MockedConstruction<DicomInputStream> mockDicomInputStream =
                     mockConstruction(DicomInputStream.class, (mock, context) -> {
                         when(mock.readDataset()).thenReturn(mockAttributes);
                         when(mock.readFileMetaInformation()).thenReturn(mockFmi);
                     })) {

            Object result = dicomExtractorService.extract("somePath", mockPath);

            verify(dicomHealthcareApiClient).storeDicomInstances(mockFmi, mockAttributes, gcpConfig.getPathqaStoreUrl());
            verify(barcodeUploadTrackerService).recordUpload("B123", "STUDY123", "SERIES123", "DEVICE456");
            assertNull(result);
        }
    }


    @Test
    void testGenerateShortBarcode() {
        String result = ReflectionTestUtils.invokeMethod(
                dicomExtractorService, "generateShortBarcode", "STUDYX", "SERIESX");

        assertNotNull(result);
        assertTrue(result.startsWith("BC-"));
        assertEquals(7, result.length());
    }

    @Test
    void testGenerateShortBarcode_ThrowsNoSuchAlgorithmException() {
        try (MockedStatic<MessageDigest> mockedDigest = mockStatic(MessageDigest.class)) {
            mockedDigest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("algorithm not found"));

            DicomAttributesException ex = assertThrows(DicomAttributesException.class, () -> ReflectionTestUtils.invokeMethod(
                            dicomExtractorService, "generateShortBarcode", "STUDY_ERR", "SERIES_ERR"));

            assertEquals("SHA-256 algorithm not found", ex.getErrorCode());
            assertTrue(ex.getErrorMessage().contains("algorithm not found"));
        }
    }

    @Test
    void testMoveFileToTempStore_Exception() throws Exception {

        Path tempPath = Files.createTempFile("test-io", ".dcm");
        File testFile = tempPath.toFile();
        try (MockedStatic<StorageOptions> mockedStorageOptions = mockStatic(StorageOptions.class);
             MockedConstruction<FileInputStream> mockedFileInputStream =
                     mockConstruction(FileInputStream.class, (mock, context) -> {
                         when(mock.read(any())).thenThrow(new IOException(" IO Exception"));
                     })) {

            StorageOptions.Builder mockBuilder = mock(StorageOptions.Builder.class);
            StorageOptions mockOptions = mock(StorageOptions.class);
            Storage mockStorage = mock(Storage.class);
            WriteChannel mockWriter = mock(WriteChannel.class);

            mockedStorageOptions.when(StorageOptions::newBuilder).thenReturn(mockBuilder);
            when(mockBuilder.setRetrySettings(any())).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockOptions);
            when(mockOptions.getService()).thenReturn(mockStorage);
            when(mockStorage.writer(any(BlobInfo.class))).thenReturn(mockWriter);
            DicomAttributesException ex = assertThrows(DicomAttributesException.class, () -> ReflectionTestUtils.invokeMethod(
                    dicomExtractorService, "moveFileToTempStore", "bucket-fail", testFile, "SOPX", "STUDYX", "SERIESX", "DEVICEY"));

            assertEquals("UPLOAD_EXCEPTION", ex.getErrorCode());
            assertTrue(ex.getMessage().contains(" IO Exception"));
        }
    }


    @Test
    void testCheckAttributeNull_NullAttribute() {
        DicomRequestDBObject dbObject = new DicomRequestDBObject();
        when(kafkaTopicConfig.getEmail()).thenReturn("email-topic");

        DicomAttributesException ex = assertThrows(
                DicomAttributesException.class, () -> ReflectionTestUtils.invokeMethod(
                        dicomExtractorService, "checkAttributeNull", dbObject, null, "Barcode"));

        verify(eventNotificationService, times(1))
                .sendEvent(eq("email-topic"), eq("MISSING_BARCODE"), isNull());

        assertEquals("INVALID ATTRIBUTE", ex.getErrorCode());
        assertTrue(ex.getErrorMessage().contains("Barcode"));
    }


    @Test
    void testProcessDicomDirWithDelay_BarcodeExistsInDB_ShouldSkipProcessing() throws IOException {
        when(dicomDirService.fetchMetaData(mockPath, attributes)).thenReturn(dicomDirDocument);
        when(dicomDirDocument.studyId()).thenReturn("STUDY1");
        when(studyBarcodeCache.getIfPresent("STUDY1")).thenReturn("BC001");
        when(dbService.isBarcodeExists("BC001")).thenReturn(true);

        ReflectionTestUtils.invokeMethod(dicomExtractorService, "processDicomDirWithDelay", mockPath, attributes);

        verify(dicomDirService, never()).fetchAndStoreMetaData(any(), any());
    }


    @Test
    void testProcessDicomDirWithDelay_BarcodeNotInDB_ShouldSaveImmediately() throws IOException {
        when(dicomDirService.fetchMetaData(mockPath, attributes)).thenReturn(dicomDirDocument);
        when(dicomDirDocument.studyId()).thenReturn("STUDY2");
        when(studyBarcodeCache.getIfPresent("STUDY2")).thenReturn("BC002");
        when(dbService.isBarcodeExists("BC002")).thenReturn(false);

        ReflectionTestUtils.invokeMethod(dicomExtractorService, "processDicomDirWithDelay", mockPath, attributes);

        verify(dicomDirService).fetchAndStoreMetaData(mockPath, dicomDirDocument);
    }

    @Test
    void testProcessDicomDirWithDelay_WhenDicomDirDocumentIsNull_ShouldReturn() {
        Attributes mockAttributes = mock(Attributes.class);

        when(dicomDirService.fetchMetaData(mockPath, mockAttributes)).thenReturn(null);
        DicomExtractorService spyService = Mockito.spy(dicomExtractorService);

        ReflectionTestUtils.invokeMethod(spyService, "processDicomDirWithDelay", mockPath, mockAttributes);
        verify(dbService, never()).isBarcodeExists(anyString());
        verify(dicomDirService).fetchMetaData(mockPath, mockAttributes);
    }

}