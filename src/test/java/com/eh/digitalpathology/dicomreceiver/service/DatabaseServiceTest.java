package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.DBRestClient;
import com.eh.digitalpathology.dicomreceiver.exceptions.DbConnectorExeption;
import com.eh.digitalpathology.dicomreceiver.model.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseServiceTest {

    @Mock
    private DBRestClient dbRestClient;

    @InjectMocks
    private DatabaseService databaseService;

    @BeforeEach
    void setUp() {
        databaseService.uriInsert = "dicom/insert";
    }

    @Test
    void testInsertDicomData_success() {
        DicomRequestDBObject request = new DicomRequestDBObject();
        ApiResponse<String> response = new ApiResponse<>("Success", "InsertedDicomData", "", "");

        when(dbRestClient.exchange(eq(HttpMethod.POST), eq("dicom/insert"), eq(request),
                any(ParameterizedTypeReference.class), any()))
                .thenReturn(Mono.just(response));

        String result = databaseService.insertDicomData(request, "TestService");

        assertEquals("Success", result);
        verify(dbRestClient, times(1)).exchange(eq(HttpMethod.POST), eq("dicom/insert"),
                eq(request), any(ParameterizedTypeReference.class), any());
    }

    @Test
    void testInsertDicomData_failure() {
        DicomRequestDBObject request = new DicomRequestDBObject();

        when(dbRestClient.exchange(any(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        DbConnectorExeption exception = assertThrows(DbConnectorExeption.class, () ->
                databaseService.insertDicomData(request, "TestService")
        );

        assertEquals("DB error", exception.getMessage());
    }

   /* @Test
    void testInsertDicomData_withNullResponse() {
        DicomRequestDBObject request = new DicomRequestDBObject();

        when(dbRestClient.exchange(any(), anyString(), any(), any(), any()))
                .thenReturn(Mono.empty());

        assertThrows(DbConnectorExeption.class, () ->
                databaseService.insertDicomData(request, "TestService")
        );
    }*/

    @Test
    void testSaveStorageCommitment_success() {
        StorageCommitmentTracker tracker = new StorageCommitmentTracker(
                "sopInstance-001", "seriesId-001", true, true, new Date(), "requestId-001");
        StorageCommitmentRequest request = new StorageCommitmentRequest(
                "seriesId", true, List.of(tracker));
        ApiResponse<String> response = new ApiResponse<>("Success", "savedStorageCommitment", "", "");

        when(dbRestClient.exchange(
                eq(HttpMethod.POST),
                eq("dicom/storage-commitment"),
                eq(request),
                any(ParameterizedTypeReference.class),
                isNull()
        )).thenReturn(Mono.just(response));

        String result = databaseService.saveStorageCommitment(request);

        assertEquals("Success", result);
        verify(dbRestClient, times(1)).exchange(eq(HttpMethod.POST),
                eq("dicom/storage-commitment"), eq(request),
                any(ParameterizedTypeReference.class), isNull());
    }

    @Test
    void testSaveStorageCommitment_failure() {
        StorageCommitmentTracker tracker = new StorageCommitmentTracker(
                "sopInstance-001", "seriesId-001", true, true, new Date(), "requestId-001");
        StorageCommitmentRequest request = new StorageCommitmentRequest(
                "seriesId", true, List.of(tracker));

        when(dbRestClient.exchange(any(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        DbConnectorExeption exception = assertThrows(DbConnectorExeption.class, () ->
                databaseService.saveStorageCommitment(request));

        assertEquals("DB error", exception.getMessage());
    }

    @Test
    void testSaveStorageCommitment_withEmptyTrackerList() {
        StorageCommitmentRequest request = new StorageCommitmentRequest(
                "seriesId", true, List.of());
        ApiResponse<String> response = new ApiResponse<>("Success", "saved", "", "");

//        when(dbRestClient.exchange(any(), anyString(), any(), any(), any()))
//               .thenReturn(Mono.just(response));
        doReturn(Mono.just(response))
                .when(dbRestClient)
                .exchange(any(), anyString(), any(), any(), any());


        String result = databaseService.saveStorageCommitment(request);

        assertEquals("Success", result);
    }

    @Test
    void testGetDicomInstanceBySopInstanceUid_success() {
        String sopInstanceUid = "sopInstance123";
        DicomRequestDBObject dicom = new DicomRequestDBObject();
        dicom.setSopInstanceUid(sopInstanceUid);
        dicom.setSeriesInstanceUid("seriesId123");
        dicom.setBarcode("vsa-150");
        dicom.setDicomInstanceReceivedTimestamp(new Date());
        dicom.setOriginalStudyInstanceUid("studyId123");
        dicom.setEnrichmentTimestamp(new Date());

        ApiResponse<DicomRequestDBObject> response = new ApiResponse<>("Success", dicom, "", "");

        when(dbRestClient.exchange(eq(HttpMethod.GET), eq("dicom/instance/" + sopInstanceUid),
                isNull(), any(ParameterizedTypeReference.class), isNull()))
                .thenReturn(Mono.just(response));

        Optional<DicomRequestDBObject> result = databaseService.getDicomInstanceBySopInstanceUid(sopInstanceUid);

        assertTrue(result.isPresent());
        assertEquals(sopInstanceUid, result.get().getSopInstanceUid());
        verify(dbRestClient, times(1)).exchange(eq(HttpMethod.GET),
                eq("dicom/instance/" + sopInstanceUid), isNull(),
                any(ParameterizedTypeReference.class), isNull());
    }

    @Test
    void testGetDicomInstanceBySopInstanceUid_nullResponse() {
        String sopInstanceUid = "sopInstance123";

        when(dbRestClient.exchange(eq(HttpMethod.GET), eq("dicom/instance/" + sopInstanceUid),
                isNull(), any(ParameterizedTypeReference.class), isNull()))
                .thenReturn(Mono.empty());

        Optional<DicomRequestDBObject> result = databaseService.getDicomInstanceBySopInstanceUid(sopInstanceUid);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetDicomInstanceBySopInstanceUid_nullContent() {
        String sopInstanceUid = "sopInstance123";
        ApiResponse<DicomRequestDBObject> response = new ApiResponse<>("Success", null, "", "");

        when(dbRestClient.exchange(eq(HttpMethod.GET), eq("dicom/instance/" + sopInstanceUid),
                isNull(), any(ParameterizedTypeReference.class), isNull()))
                .thenReturn(Mono.just(response));

        Optional<DicomRequestDBObject> result = databaseService.getDicomInstanceBySopInstanceUid(sopInstanceUid);

        assertTrue(result.isEmpty());
    }

    @Test
    void testGetDicomInstanceBySopInstanceUid_exception() {
        String sopInstanceUid = "sopInstance123";

        when(dbRestClient.exchange(eq(HttpMethod.GET), eq("dicom/instance/" + sopInstanceUid),
                isNull(), any(ParameterizedTypeReference.class), isNull()))
                .thenThrow(new RuntimeException("Connection error"));

        Optional<DicomRequestDBObject> result = databaseService.getDicomInstanceBySopInstanceUid(sopInstanceUid);

        assertTrue(result.isEmpty());
    }

    @Test
    void testSaveMetaDataInfo_success() {
        DicomDirDocument document = new DicomDirDocument("1", "studyId", 3, new byte[3]);
        ApiResponse<String> response = new ApiResponse<>("Success", "InsertedDicomData", "", "");

        when(dbRestClient.exchange(eq(HttpMethod.POST), eq("dicom/dicomdir"),
                eq(document), any(ParameterizedTypeReference.class), isNull()
        )).thenReturn(Mono.just(response));

        String result = databaseService.saveMetaDataInfo(document);

        assertEquals("Success", result);
        verify(dbRestClient, times(1)).exchange(eq(HttpMethod.POST),
                eq("dicom/dicomdir"), eq(document),
                any(ParameterizedTypeReference.class), isNull());
    }

    @Test
    void testSaveMetaDataInfo_failure() {
        DicomDirDocument document = new DicomDirDocument("1", "studyId", 3, new byte[3]);

        when(dbRestClient.exchange(any(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB error"));

        DbConnectorExeption exception = assertThrows(DbConnectorExeption.class, () ->
                databaseService.saveMetaDataInfo(document));

        assertEquals("DB error", exception.getMessage());
    }

    @Test
    void testSaveMetaDataInfo_withLargeDocument() {
        DicomDirDocument document = new DicomDirDocument("1", "studyId", 1000, new byte[1000]);
        ApiResponse<String> response = new ApiResponse<>("Success", "saved", "", "");

        doReturn(Mono.just(response))
                .when(dbRestClient)
                .exchange(any(), anyString(), any(), any(), any());


        String result = databaseService.saveMetaDataInfo(document);

        assertEquals("Success", result);
    }

    @Test
    void testIsBarcodeExists_success() {
        String barcode = "B123";
        ApiResponse<Boolean> response = new ApiResponse<>("Success", true, "", "");

        when(dbRestClient.exchange(eq(HttpMethod.GET), eq("qaslide/barcode/" + barcode),
                isNull(), any(ParameterizedTypeReference.class), isNull()
        )).thenReturn(Mono.just(response));

        Boolean result = databaseService.isBarcodeExists(barcode);

        assertTrue(result);
        verify(dbRestClient, times(1)).exchange(eq(HttpMethod.GET),
                eq("qaslide/barcode/" + barcode), isNull(),
                any(ParameterizedTypeReference.class), isNull());
    }

    @Test
    void testIsBarcodeExists_doesNotExist() {
        String barcode = "B456";
        ApiResponse<Boolean> response = new ApiResponse<>("Success", false, "", "");

        when(dbRestClient.exchange(eq(HttpMethod.GET), eq("qaslide/barcode/" + barcode),
                isNull(), any(ParameterizedTypeReference.class), isNull()
        )).thenReturn(Mono.just(response));

        Boolean result = databaseService.isBarcodeExists(barcode);

        assertFalse(result);
    }

    @Test
    void testIsBarcodeExists_failure() {
        String barcode = "B123";

        when(dbRestClient.exchange(eq(HttpMethod.GET), eq("qaslide/barcode/" + barcode),
                isNull(), any(ParameterizedTypeReference.class), isNull()
        )).thenThrow(new RuntimeException("DB error"));

        DbConnectorExeption exception = assertThrows(DbConnectorExeption.class,
                () -> databaseService.isBarcodeExists(barcode));

        assertEquals("DB error", exception.getMessage());
    }
    @Disabled
    @Test
    void testIsBarcodeExists_nullResponse() {
        String barcode = "B789";

        when(dbRestClient.exchange(eq(HttpMethod.GET), eq("qaslide/barcode/" + barcode),
                isNull(), any(ParameterizedTypeReference.class), isNull()
        )).thenReturn(Mono.empty());

        assertThrows(DbConnectorExeption.class,
                () -> databaseService.isBarcodeExists(barcode));
    }

    @Test
    @Disabled
    void testFetchScannerByDeviceSerialNumber_success() {
        String deviceSerialNumber = "SN12345";
        //SlideScanner scanner = new SlideScanner();

        SlideScanner scanner = new SlideScanner("id123", "name", "model", "vendor",
                "location", "aeTitle", "ip", "serialNumber", "firmwareVersion", true, false);

        // Assuming SlideScanner has a setDeviceSerialNumber method
        ApiResponse<SlideScanner> response = new ApiResponse<>("Success", scanner, "", "");

        when(dbRestClient.exchange(eq(HttpMethod.GET), eq("scanner/" + deviceSerialNumber),
                isNull(), any(ParameterizedTypeReference.class), isNull()))
                .thenReturn(Mono.just(response));

        SlideScanner result = databaseService.fetchScannerByDeviceSerialNumber(deviceSerialNumber);

        assertNotNull(result);
        assertEquals(scanner, result);
        verify(dbRestClient, times(1)).exchange(eq(HttpMethod.GET),
                eq("scanner/" + deviceSerialNumber), isNull(),
                any(ParameterizedTypeReference.class), isNull());
    }

    @Test
    void testFetchScannerByDeviceSerialNumber_nullResponse() {
        String deviceSerialNumber = "SN12345";

        when(dbRestClient.exchange(eq(HttpMethod.GET), eq("scanner/" + deviceSerialNumber),
                isNull(), any(ParameterizedTypeReference.class), isNull()))
                .thenReturn(Mono.empty());

        SlideScanner result = databaseService.fetchScannerByDeviceSerialNumber(deviceSerialNumber);

        assertNull(result);
    }

    @Test
    void testFetchScannerByDeviceSerialNumber_nullContent() {
        String deviceSerialNumber = "SN12345";
        ApiResponse<SlideScanner> response = new ApiResponse<>("Success", null, "", "");

        when(dbRestClient.exchange(eq(HttpMethod.GET), eq("scanner/" + deviceSerialNumber),
                isNull(), any(ParameterizedTypeReference.class), isNull()))
                .thenReturn(Mono.just(response));

        SlideScanner result = databaseService.fetchScannerByDeviceSerialNumber(deviceSerialNumber);

        assertNull(result);
    }

    @Test
    void testFetchScannerByDeviceSerialNumber_exception() {
        String deviceSerialNumber = "SN12345";

        when(dbRestClient.exchange(eq(HttpMethod.GET), eq("scanner/" + deviceSerialNumber),
                isNull(), any(ParameterizedTypeReference.class), isNull()))
                .thenThrow(new RuntimeException("Connection error"));

        SlideScanner result = databaseService.fetchScannerByDeviceSerialNumber(deviceSerialNumber);

        assertNull(result);
    }

    @Test
    void testInsertDicomData_withDifferentServiceNames() {
        DicomRequestDBObject request = new DicomRequestDBObject();
        ApiResponse<String> response = new ApiResponse<>("Success", "Inserted", "", "");

        doReturn(Mono.just(response))
                .when(dbRestClient)
                .exchange(any(), anyString(), any(), any(), any());


        String result1 = databaseService.insertDicomData(request, "Service1");
        String result2 = databaseService.insertDicomData(request, "Service2");
        String result3 = databaseService.insertDicomData(request, "Service3");

        assertEquals("Success", result1);
        assertEquals("Success", result2);
        assertEquals("Success", result3);
        verify(dbRestClient, times(3)).exchange(any(), anyString(), any(), any(), any());
    }
}
