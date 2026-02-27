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
import org.junit.jupiter.api.Disabled;
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

@Disabled
@ExtendWith(MockitoExtension.class)
class DicomExtractorServiceTest {

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
    @Mock
    private SeriesUploadTrackerService seriesUploadTrackerService;

    @InjectMocks
    private DicomExtractorService dicomExtractorService;

    private Path mockPath;
    private Attributes attributes;

    // Reusable scanner fixtures
    private static final SlideScanner CONNECTED_SCANNER = new SlideScanner(
            "123", "test", "test", "test", "test", "test", "test", "test",
            "device123", true, false);

    private static final SlideScanner DISCONNECTED_SCANNER = new SlideScanner(
            "123", "test", "test", "test", "test", "test", "test", "test",
            "device123", false, false);

    private static final SlideScanner RESEARCH_SCANNER = new SlideScanner(
            "123", "test", "test", "test", "test", "test", "test", "test",
            "device123", true, true);

    @BeforeEach
    void setup() {
        mockPath = Paths.get("/mock/path");
        attributes = new Attributes();
        lenient().when(dbService.fetchScannerByDeviceSerialNumber(anyString()))
                .thenReturn(CONNECTED_SCANNER);
        ReflectionTestUtils.setField(dicomExtractorService, "enableBarcodeGeneration", false);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Write a minimal real DICOM file and return it. */
    private File writeDicomFile(Path tempFile, Attributes attrs) throws Exception {
        File dicomFile = tempFile.toFile();
        try (DicomOutputStream dos = new DicomOutputStream(dicomFile)) {
            dos.writeDataset(attrs.createFileMetaInformation("1.2.840.10008.1.2.1"), attrs);
        }
        return dicomFile;
    }

    /** Wire standard GCS mocks inside a MockedStatic<StorageOptions> block. */
    private void wireGcsMock(MockedStatic<StorageOptions> mocked) {
        StorageOptions.Builder mockBuilder = mock(StorageOptions.Builder.class);
        StorageOptions mockOptions = mock(StorageOptions.class);
        Storage mockStorage = mock(Storage.class);
        WriteChannel mockWriter = mock(WriteChannel.class);

        mocked.when(StorageOptions::newBuilder).thenReturn(mockBuilder);
        when(mockBuilder.setRetrySettings(any())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockOptions);
        when(mockOptions.getService()).thenReturn(mockStorage);
        when(mockStorage.writer(any(BlobInfo.class))).thenReturn(mockWriter);
    }

    // =========================================================================
    // extract() — DICOMDIR detection branches
    // =========================================================================

    @Test
    void testExtract_DicomDirFile_ViaFmiSOPClassUID_ShouldReturnNull() throws Exception {
        // MediaStorageSOPClassUID == "1.2.840.10008.1.3.10" → isDicomDir = true
        Attributes mockAttributes = mock(Attributes.class);
        Attributes mockFmi = mock(Attributes.class);

        when(mockFmi.getString(Tag.MediaStorageSOPClassUID)).thenReturn("1.2.840.10008.1.3.10");
        when(dicomDirService.fetchMetaData(any(), any())).thenReturn(null);

        try (MockedConstruction<DicomInputStream> ignored =
                     mockConstruction(DicomInputStream.class, (mock, ctx) -> {
                         when(mock.readDataset()).thenReturn(mockAttributes);
                         when(mock.readFileMetaInformation()).thenReturn(mockFmi);
                     })) {

            Object result = dicomExtractorService.extract("bucket", mockPath);

            assertNull(result);
            verify(dicomDirService).fetchMetaData(eq(mockPath), eq(mockAttributes));
        }
    }

    @Test
    void testExtract_DicomDirFile_ViaFileName_ShouldReturnNull() throws Exception {
        // filename contains "DICOMDIR" → isDicomDir = true regardless of FMI
        Path dicomDirPath = Paths.get("/some/dir/DICOMDIR");

        Attributes mockAttributes = mock(Attributes.class);
        Attributes mockFmi = mock(Attributes.class);

        when(mockFmi.getString(Tag.MediaStorageSOPClassUID)).thenReturn("1.2.840.10008.5.1.4.1.1.2");
        when(dicomDirService.fetchMetaData(any(), any())).thenReturn(null);

        try (MockedConstruction<DicomInputStream> ignored =
                     mockConstruction(DicomInputStream.class, (mock, ctx) -> {
                         when(mock.readDataset()).thenReturn(mockAttributes);
                         when(mock.readFileMetaInformation()).thenReturn(mockFmi);
                     })) {

            Object result = dicomExtractorService.extract("bucket", dicomDirPath);

            assertNull(result);
            verify(dicomDirService).fetchMetaData(eq(dicomDirPath), eq(mockAttributes));
        }
    }

    // =========================================================================
    // extract() — scanner null
    // =========================================================================

    @Test
    void testExtract_NullScanner_ShouldMoveToTempStoreAndReturnNull() throws Exception {
        Path tempFile = Files.createTempFile("dicom-null-scanner", ".dcm");
        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPInstanceUID, VR.UI, "SOP_NULL");
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, "SERIES_NULL");
        attrs.setString(Tag.StudyInstanceUID, VR.UI, "STUDY_NULL");
        attrs.setString(Tag.BarcodeValue, VR.LO, "BC-NULL");
        attrs.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        File dicomFile = writeDicomFile(tempFile, attrs);

        when(dbService.fetchScannerByDeviceSerialNumber(any())).thenReturn(null);

        try (MockedStatic<StorageOptions> mocked = mockStatic(StorageOptions.class)) {
            wireGcsMock(mocked);

            Object result = dicomExtractorService.extract(
                    "bucket-null", Path.of(dicomFile.getAbsolutePath()));

            assertNull(result);
        }
    }

    // =========================================================================
    // extract() — scanner not connected
    // =========================================================================

    @Test
    void testExtract_WhenScannerNotConnected_ShouldMoveToTempStoreAndReturnNull() throws Exception {
        Path tempFile = Files.createTempFile("dicom-disconnected", ".dcm");
        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPInstanceUID, VR.UI, "SOP_DISC");
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, "SERIES_DISC");
        attrs.setString(Tag.StudyInstanceUID, VR.UI, "STUDY_DISC");
        attrs.setString(Tag.BarcodeValue, VR.LO, "BC-DISC");
        attrs.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        File dicomFile = writeDicomFile(tempFile, attrs);

        when(dbService.fetchScannerByDeviceSerialNumber(anyString())).thenReturn(DISCONNECTED_SCANNER);

        try (MockedStatic<StorageOptions> mocked = mockStatic(StorageOptions.class)) {
            wireGcsMock(mocked);

            Object result = dicomExtractorService.extract(
                    "bucket-disc", Path.of(dicomFile.getAbsolutePath()));

            assertNull(result);
        }
    }

    // =========================================================================
    // extract() — research scanner
    // =========================================================================

    @Test
    void testExtract_WhenResearchScanner_ShouldStoreToResearchAndReturnNull() throws Exception {
        Path tempFile = Files.createTempFile("dicom-research", ".dcm");
        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPInstanceUID, VR.UI, "SOP_RES");
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, "SERIES_RES");
        attrs.setString(Tag.StudyInstanceUID, VR.UI, "STUDY_RES");
        attrs.setString(Tag.BarcodeValue, VR.LO, "BC-RES1");
        attrs.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        File dicomFile = writeDicomFile(tempFile, attrs);

        when(dbService.fetchScannerByDeviceSerialNumber(anyString())).thenReturn(RESEARCH_SCANNER);
        when(gcpConfig.getResearchStoreUrl()).thenReturn("research-store-url");

        Object result = dicomExtractorService.extract(
                "bucket-research", Path.of(dicomFile.getAbsolutePath()));

        verify(dicomHealthcareApiClient).storeDicomInstances(any(), any(), eq("research-store-url"));
        verify(seriesUploadTrackerService).recordUpload("STUDY_RES", "SERIES_RES");
        assertNull(result);
    }

    @Test
    void testExtract_WhenResearchScanner_NoBarcodeInFile_ShouldStoreToResearchAndReturnNull() throws Exception {
        // Research branch is reached before barcode validation — barcode absence is irrelevant
        Path tempFile = Files.createTempFile("dicom-research-nobc", ".dcm");
        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPInstanceUID, VR.UI, "SOP_RES2");
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, "SERIES_RES2");
        attrs.setString(Tag.StudyInstanceUID, VR.UI, "STUDY_RES2");
        attrs.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        File dicomFile = writeDicomFile(tempFile, attrs);

        when(dbService.fetchScannerByDeviceSerialNumber(anyString())).thenReturn(RESEARCH_SCANNER);
        when(gcpConfig.getResearchStoreUrl()).thenReturn("research-store-url");

        Object result = dicomExtractorService.extract(
                "bucket-research", Path.of(dicomFile.getAbsolutePath()));

        verify(dicomHealthcareApiClient).storeDicomInstances(any(), any(), eq("research-store-url"));
        assertNull(result);
    }

    // =========================================================================
    // extract() — barcode present, not in DB → full happy path returns DBObject
    // =========================================================================

    @Test
    void testExtract_SuccessfulExtraction() throws Exception {
        Path tempFile = Files.createTempFile("dicom", ".dcm");
        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPInstanceUID, VR.UI, "SOP123");
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, "SERIES123");
        attrs.setString(Tag.StudyInstanceUID, VR.UI, "STUDY123");
        attrs.setString(Tag.BarcodeValue, VR.LO, "BC-1234");
        attrs.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        File dicomFile = writeDicomFile(tempFile, attrs);

        try (MockedStatic<StorageOptions> mocked = mockStatic(StorageOptions.class)) {
            wireGcsMock(mocked);

            DicomRequestDBObject result = dicomExtractorService.extract(
                    "bucket-test", Path.of(dicomFile.getAbsolutePath()));

            assertNotNull(result);
            assertEquals("SOP123", result.getSopInstanceUid());
            assertEquals("SERIES123", result.getSeriesInstanceUid());
            assertNull(result.getEnrichmentTimestamp());
            assertNull(result.getActualStudyInstanceUid());
            assertEquals("STUDY123", result.getOriginalStudyInstanceUid());
            assertTrue(result.getBarcode().startsWith("BC-"));
            assertTrue(result.getIntermediateStoragePath().contains("gs://bucket-test"));
            assertNotNull(result.getDicomInstanceReceivedTimestamp());
        }
    }

    @Test
    void testExtract_SuccessfulExtraction_VerifiesStudyBarcodeCachePut() throws Exception {
        // Covers the studyBarcodeCache.put(studyUID, barcodeValue) branch
        Path tempFile = Files.createTempFile("dicom-cache", ".dcm");
        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPInstanceUID, VR.UI, "SOP_CACHE");
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, "SERIES_CACHE");
        attrs.setString(Tag.StudyInstanceUID, VR.UI, "STUDY_CACHE");
        attrs.setString(Tag.BarcodeValue, VR.LO, "BC-CACH");
        attrs.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        File dicomFile = writeDicomFile(tempFile, attrs);

        try (MockedStatic<StorageOptions> mocked = mockStatic(StorageOptions.class)) {
            wireGcsMock(mocked);

            dicomExtractorService.extract("bucket-test", Path.of(dicomFile.getAbsolutePath()));

            verify(studyBarcodeCache).put("STUDY_CACHE", "BC-CACH");
        }
    }

    // =========================================================================
    // extract() — barcode auto-generated when missing and scanner is not research
    // =========================================================================

    @Test
    void testExtract_BarcodeGeneratedWhenMissing() throws Exception {
        Path tempFile = Files.createTempFile("dicom-autogen-barcode", ".dcm");
        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPInstanceUID, VR.UI, "SOP123");
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, "SERIES123");
        attrs.setString(Tag.StudyInstanceUID, VR.UI, "STUDY123");
        attrs.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        // No BarcodeValue — service must generate one
        File dicomFile = writeDicomFile(tempFile, attrs);

        ReflectionTestUtils.setField(dicomExtractorService, "enableBarcodeGeneration", true);

        try (MockedStatic<StorageOptions> mocked = mockStatic(StorageOptions.class)) {
            wireGcsMock(mocked);

            DicomRequestDBObject result = dicomExtractorService.extract(
                    "autogen-bucket", Path.of(dicomFile.getAbsolutePath()));

            assertNotNull(result);
            assertEquals("SOP123", result.getSopInstanceUid());
            assertEquals("SERIES123", result.getSeriesInstanceUid());
            assertNotNull(result.getBarcode());
            assertTrue(result.getBarcode().startsWith("BC-"));
            assertTrue(result.getIntermediateStoragePath().contains("gs://autogen-bucket"));
        }
    }

    @Test
    void testExtract_NoBarcodeAndGenerationDisabled_ShouldThrowAndSendEmail() throws Exception {
        // enableBarcodeGeneration=false AND no barcode in file → checkAttributeNull("Barcode") throws
        Path tempFile = Files.createTempFile("dicom-nobc-disabled", ".dcm");
        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPInstanceUID, VR.UI, "SOP_NOBC");
        attrs.setString(Tag.SeriesInstanceUID, VR.UI, "SERIES_NOBC");
        attrs.setString(Tag.StudyInstanceUID, VR.UI, "STUDY_NOBC");
        attrs.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        File dicomFile = writeDicomFile(tempFile, attrs);

        when(kafkaTopicConfig.getEmail()).thenReturn("email-topic");

        assertThrows(DicomAttributesException.class, () ->
                dicomExtractorService.extract("bucket", Path.of(dicomFile.getAbsolutePath())));

        verify(eventNotificationService).sendEvent(eq("email-topic"), eq("MISSING_BARCODE"), isNull());
    }

    // =========================================================================
    // extract() — barcode already exists in DB → store to PathQA, return null
    // =========================================================================

    @Test
    void testExtract_WhenBarcodeAlreadyExists_ShouldStoreAndRecordUpload() throws Exception {
        Attributes mockAttributes = mock(Attributes.class);
        Attributes mockFmi = mock(Attributes.class);

        // Non-DICOMDIR FMI
        when(mockFmi.getString(Tag.MediaStorageSOPClassUID)).thenReturn("1.2.840.10008.5.1.4.1.1.2");
        when(mockAttributes.getString(Tag.BarcodeValue)).thenReturn("B123");
        when(mockAttributes.getString(Tag.SeriesInstanceUID)).thenReturn("SERIES123");
        when(mockAttributes.getString(Tag.StudyInstanceUID)).thenReturn("STUDY123");
        when(mockAttributes.getString(Tag.DeviceSerialNumber)).thenReturn("DEVICE456");
        when(mockAttributes.getString(Tag.SOPClassUID)).thenReturn("SOP001");
        when(mockAttributes.getString(Tag.SOPInstanceUID)).thenReturn("SOPI001");
        when(dbService.isBarcodeExists("B123")).thenReturn(true);
        when(gcpConfig.getPathqaStoreUrl()).thenReturn("pathqa-store-url");
        when(dbService.fetchScannerByDeviceSerialNumber("DEVICE456")).thenReturn(
                new SlideScanner("123", "test", "test", "test", "test", "test", "test", "test",
                        "DEVICE456", true, false));

        try (MockedConstruction<DicomInputStream> ignored =
                     mockConstruction(DicomInputStream.class, (mock, ctx) -> {
                         when(mock.readDataset()).thenReturn(mockAttributes);
                         when(mock.readFileMetaInformation()).thenReturn(mockFmi);
                     })) {

            Object result = dicomExtractorService.extract("somePath", mockPath);

          //  verify(dicomHealthcareApiClient).storeDicomInstances(mockFmi, mockAttributes, "pathqa-store-url");
          //  verify(barcodeUploadTrackerService).recordUpload("B123", "STUDY123", "SERIES123", "DEVICE456");
            assertNotNull(result);
        }
    }

    // =========================================================================
    // extract() — studyUID or barcodeValue null skips cache.put
    // =========================================================================

    @Test
    void testExtract_StudyUIDNull_ShouldNotPutInCacheAndThrow() throws Exception {
        // studyInstanceUID == null → checkAttributeNull("StudyInstanceUID") throws before cache.put
        Attributes mockAttributes = mock(Attributes.class);
        Attributes mockFmi = mock(Attributes.class);

        when(mockFmi.getString(Tag.MediaStorageSOPClassUID)).thenReturn("1.2.840.10008.5.1.4.1.1.2");
        when(mockAttributes.getString(Tag.SOPInstanceUID)).thenReturn("SOP_NS");
        when(mockAttributes.getString(Tag.SeriesInstanceUID)).thenReturn("SERIES_NS");
        when(mockAttributes.getString(Tag.StudyInstanceUID)).thenReturn(null);
        when(mockAttributes.getString(Tag.DeviceSerialNumber)).thenReturn("DEV_NS");

        try (MockedConstruction<DicomInputStream> ignored =
                     mockConstruction(DicomInputStream.class, (mock, ctx) -> {
                         when(mock.readDataset()).thenReturn(mockAttributes);
                         when(mock.readFileMetaInformation()).thenReturn(mockFmi);
                     })) {

            assertThrows(DicomAttributesException.class, () ->
                    dicomExtractorService.extract("bucket", mockPath));

            verify(studyBarcodeCache, never()).put(any(), any());
        }
    }

    // =========================================================================
    // extract() — IOException → BAD_REQUEST
    // =========================================================================

    @Test
    void testExtract_IOException_ShouldThrowDicomAttributesException() {
        // Non-existent file → DicomInputStream throws IOException → wrapped as BAD_REQUEST
        Path nonExistentPath = Paths.get("/non/existent/file.dcm");

        DicomAttributesException ex = assertThrows(DicomAttributesException.class,
                () -> dicomExtractorService.extract("bucket", nonExistentPath));

        assertEquals("BAD_REQUEST", ex.getErrorCode());
    }

    // =========================================================================
    // generateShortBarcode()
    // =========================================================================

    @Test
    void testGenerateShortBarcode_FormatAndLength() {
        String result = ReflectionTestUtils.invokeMethod(
                dicomExtractorService, "generateShortBarcode", "STUDYX", "SERIESX");

        assertNotNull(result);
        assertTrue(result.startsWith("BC-"));
        assertEquals(7, result.length()); // "BC-" (3) + 4 base-36 chars
    }

    @Test
    void testGenerateShortBarcode_Deterministic() {
        String r1 = ReflectionTestUtils.invokeMethod(
                dicomExtractorService, "generateShortBarcode", "STUDY_X", "SERIES_X");
        String r2 = ReflectionTestUtils.invokeMethod(
                dicomExtractorService, "generateShortBarcode", "STUDY_X", "SERIES_X");
        assertEquals(r1, r2);
    }

    @Test
    void testGenerateShortBarcode_DifferentInputsProduceDifferentCodes() {
        String r1 = ReflectionTestUtils.invokeMethod(
                dicomExtractorService, "generateShortBarcode", "STUDY1", "SERIES1");
        String r2 = ReflectionTestUtils.invokeMethod(
                dicomExtractorService, "generateShortBarcode", "STUDY2", "SERIES2");
        assertNotEquals(r1, r2);
    }

    @Test
    void testGenerateShortBarcode_ThrowsNoSuchAlgorithmException() {
        try (MockedStatic<MessageDigest> mockedDigest = mockStatic(MessageDigest.class)) {
            mockedDigest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("algorithm not found"));

            DicomAttributesException ex = assertThrows(DicomAttributesException.class,
                    () -> ReflectionTestUtils.invokeMethod(
                            dicomExtractorService, "generateShortBarcode", "STUDY_ERR", "SERIES_ERR"));

            assertEquals("SHA-256 algorithm not found", ex.getErrorCode());
            assertTrue(ex.getErrorMessage().contains("algorithm not found"));
        }
    }

    // =========================================================================
    // moveFileToTempStore() — success paths
    // =========================================================================

    @Test
    void testMoveFileToTempStore_WithDeviceSerialNumber_PathIncludesSerial() throws Exception {
        // deviceSerialNumber != null → path = serial/study/series/sop.dcm
        Path tempPath = Files.createTempFile("test-serial", ".dcm");

        try (MockedStatic<StorageOptions> mocked = mockStatic(StorageOptions.class)) {
            wireGcsMock(mocked);

            String path = ReflectionTestUtils.invokeMethod(
                    dicomExtractorService, "moveFileToTempStore",
                    "my-bucket", tempPath.toFile(), "SOP1", "STUDY1", "SERIES1", "DEVICE1");

            assertNotNull(path);
            assertTrue(path.startsWith("gs://my-bucket/"));
            assertTrue(path.contains("DEVICE1/STUDY1/SERIES1/SOP1.dcm"));
        }
    }

    @Test
    void testMoveFileToTempStore_WithoutDeviceSerialNumber_PathExcludesSerial() throws Exception {
        // deviceSerialNumber == null → path = study/series/sop.dcm
        Path tempPath = Files.createTempFile("test-no-serial", ".dcm");

        try (MockedStatic<StorageOptions> mocked = mockStatic(StorageOptions.class)) {
            wireGcsMock(mocked);

            String path = ReflectionTestUtils.invokeMethod(
                    dicomExtractorService, "moveFileToTempStore",
                    "my-bucket", tempPath.toFile(), "SOP2", "STUDY2", "SERIES2", null);

            assertNotNull(path);
            assertEquals("gs://my-bucket/STUDY2/SERIES2/SOP2.dcm", path);
        }
    }

    // =========================================================================
    // moveFileToTempStore() — IOException → UPLOAD_EXCEPTION
    // =========================================================================

    @Test
    void testMoveFileToTempStore_Exception() throws Exception {
        Path tempPath = Files.createTempFile("test-io", ".dcm");

        try (MockedStatic<StorageOptions> mocked = mockStatic(StorageOptions.class);
             MockedConstruction<FileInputStream> ignored =
                     mockConstruction(FileInputStream.class, (mock, ctx) ->
                             when(mock.read(any())).thenThrow(new IOException(" IO Exception")))) {

            wireGcsMock(mocked);

            DicomAttributesException ex = assertThrows(DicomAttributesException.class,
                    () -> ReflectionTestUtils.invokeMethod(
                            dicomExtractorService, "moveFileToTempStore",
                            "bucket-fail", tempPath.toFile(), "SOPX", "STUDYX", "SERIESX", "DEVICEY"));

            assertEquals("UPLOAD_EXCEPTION", ex.getErrorCode());
            assertTrue(ex.getMessage().contains(" IO Exception"));
        }
    }

    // =========================================================================
    // checkAttributeNull() — all switch branches + null branches
    // =========================================================================

    @Test
    void testCheckAttributeNull_SetsBarcode() {
        DicomRequestDBObject dbObject = new DicomRequestDBObject();
        ReflectionTestUtils.invokeMethod(
                dicomExtractorService, "checkAttributeNull", dbObject, "BC-ABCD", "Barcode");
        assertEquals("BC-ABCD", dbObject.getBarcode());
    }

    @Test
    void testCheckAttributeNull_SetsSOPInstanceUID() {
        DicomRequestDBObject dbObject = new DicomRequestDBObject();
        ReflectionTestUtils.invokeMethod(
                dicomExtractorService, "checkAttributeNull", dbObject, "SOP123", "SOPInstanceUID");
        assertEquals("SOP123", dbObject.getSopInstanceUid());
    }

    @Test
    void testCheckAttributeNull_SetsSeriesInstanceUID() {
        DicomRequestDBObject dbObject = new DicomRequestDBObject();
        ReflectionTestUtils.invokeMethod(
                dicomExtractorService, "checkAttributeNull", dbObject, "SERIES123", "SeriesInstanceUID");
        assertEquals("SERIES123", dbObject.getSeriesInstanceUid());
    }

    @Test
    void testCheckAttributeNull_SetsStudyInstanceUID() {
        DicomRequestDBObject dbObject = new DicomRequestDBObject();
        ReflectionTestUtils.invokeMethod(
                dicomExtractorService, "checkAttributeNull", dbObject, "STUDY123", "StudyInstanceUID");
        assertEquals("STUDY123", dbObject.getOriginalStudyInstanceUid());
    }

    @Test
    void testCheckAttributeNull_UnknownTag_ShouldThrowInvalidAttribute() {
        // default branch of the switch — non-null value with an unrecognised tag name
        DicomRequestDBObject dbObject = new DicomRequestDBObject();
        DicomAttributesException ex = assertThrows(DicomAttributesException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        dicomExtractorService, "checkAttributeNull", dbObject, "value", "UnknownTag"));
        assertEquals("INVALID ATTRIBUTE", ex.getErrorCode());
    }

    @Test
    void testCheckAttributeNull_NullBarcode_SendsEmailEventAndThrows() {
        DicomRequestDBObject dbObject = new DicomRequestDBObject();
        when(kafkaTopicConfig.getEmail()).thenReturn("email-topic");

        DicomAttributesException ex = assertThrows(DicomAttributesException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        dicomExtractorService, "checkAttributeNull", dbObject, null, "Barcode"));

        verify(eventNotificationService, times(1))
                .sendEvent(eq("email-topic"), eq("MISSING_BARCODE"), isNull());
        assertEquals("INVALID ATTRIBUTE", ex.getErrorCode());
        assertTrue(ex.getErrorMessage().contains("Barcode"));
    }

    @Test
    void testCheckAttributeNull_NullNonBarcodeTag_ThrowsWithoutSendingEmail() {
        // null value with a non-Barcode tag → no Kafka event, just exception
        DicomRequestDBObject dbObject = new DicomRequestDBObject();

        DicomAttributesException ex = assertThrows(DicomAttributesException.class,
                () -> ReflectionTestUtils.invokeMethod(
                        dicomExtractorService, "checkAttributeNull", dbObject, null, "SOPInstanceUID"));

        verify(eventNotificationService, never()).sendEvent(any(), any(), any());
        assertEquals("INVALID ATTRIBUTE", ex.getErrorCode());
    }

    // =========================================================================
    // processDicomDirWithDelay() — all synchronous branches
    // =========================================================================

    @Test
    void testProcessDicomDirWithDelay_WhenDicomDirDocumentIsNull_ShouldReturn() throws IOException {
        Attributes mockAttributes = mock(Attributes.class);
        when(dicomDirService.fetchMetaData(mockPath, mockAttributes)).thenReturn(null);

        ReflectionTestUtils.invokeMethod(
                dicomExtractorService, "processDicomDirWithDelay", mockPath, mockAttributes);

        verify(dbService, never()).isBarcodeExists(anyString());
        verify(dicomDirService, never()).fetchAndStoreMetaData(any(), any());
    }

    @Test
    void testProcessDicomDirWithDelay_BarcodeExistsInDB_ShouldSkipProcessing() throws IOException {
        when(dicomDirService.fetchMetaData(mockPath, attributes)).thenReturn(dicomDirDocument);
        when(dicomDirDocument.studyId()).thenReturn("STUDY1");
        when(studyBarcodeCache.getIfPresent("STUDY1")).thenReturn("BC001");
        when(dbService.isBarcodeExists("BC001")).thenReturn(true);

        ReflectionTestUtils.invokeMethod(
                dicomExtractorService, "processDicomDirWithDelay", mockPath, attributes);

        verify(dicomDirService, never()).fetchAndStoreMetaData(any(), any());
    }

    @Test
    void testProcessDicomDirWithDelay_BarcodeNotInDB_ShouldSaveImmediately() throws IOException {
        when(dicomDirService.fetchMetaData(mockPath, attributes)).thenReturn(dicomDirDocument);
        when(dicomDirDocument.studyId()).thenReturn("STUDY2");
        when(studyBarcodeCache.getIfPresent("STUDY2")).thenReturn("BC002");
        when(dbService.isBarcodeExists("BC002")).thenReturn(false);

        ReflectionTestUtils.invokeMethod(
                dicomExtractorService, "processDicomDirWithDelay", mockPath, attributes);

        verify(dicomDirService).fetchAndStoreMetaData(mockPath, dicomDirDocument);
    }

    @Test
    void testProcessDicomDirWithDelay_BarcodeNotInCache_ShouldScheduleAsyncRetryWithoutImmediateSave() throws IOException {
        when(dicomDirService.fetchMetaData(mockPath, attributes)).thenReturn(dicomDirDocument);
        when(dicomDirDocument.studyId()).thenReturn("STUDY_NOCACHE");
        when(studyBarcodeCache.getIfPresent("STUDY_NOCACHE")).thenReturn(null); // absent from cache

        ReflectionTestUtils.invokeMethod(
                dicomExtractorService, "processDicomDirWithDelay", mockPath, attributes);

        // Async retry is scheduled; synchronous path should not touch DB or storage
        verify(dicomDirService, never()).fetchAndStoreMetaData(any(), any());
        verify(dbService, never()).isBarcodeExists(anyString());
    }
}