package com.eh.digitalpathology.dicomreceiver.service.storescp;

import com.eh.digitalpathology.dicomreceiver.config.KafkaTopicConfig;
import com.eh.digitalpathology.dicomreceiver.model.DicomRequestDBObject;
import com.eh.digitalpathology.dicomreceiver.model.StorageCommitmentRequest;
import com.eh.digitalpathology.dicomreceiver.model.StorageCommitmentTracker;
import com.eh.digitalpathology.dicomreceiver.service.DatabaseService;
import com.eh.digitalpathology.dicomreceiver.service.EventNotificationService;
import com.eh.digitalpathology.dicomreceiver.util.StorageCommitmentUtils;
import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.DicomServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageCommitmentSCPServiceTest {

    @Mock
    private DatabaseService databaseService;

    @Mock
    private EventNotificationService eventNotificationService;

    @Mock
    KafkaTopicConfig kafkaTopicConfig;

    @Mock
    private Association association;

    @Mock
    private PresentationContext presentationContext;

    private StorageCommitmentSCPService service;
    private Attributes baseActionInfo;
    private StorageCommitmentTracker tracker;
    private StorageCommitmentRequest request;

    @BeforeEach
    void setup() {
        service = new StorageCommitmentSCPService(databaseService, eventNotificationService, kafkaTopicConfig);
        baseActionInfo = new Attributes();
        baseActionInfo.setString(Tag.TransactionUID, VR.UI, "1.2.3.4.5");
        baseActionInfo.setString(Tag.SOPClassUID, VR.UI, "1.2.840.10008.1.20.1");
        baseActionInfo.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5.6.7.8.9");

        Sequence refSOPSeq = baseActionInfo.newSequence(Tag.ReferencedSOPSequence, 1);
        Attributes sopItem = new Attributes();
        sopItem.setString(Tag.ReferencedSOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        sopItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.3.4.5.6");
        refSOPSeq.add(sopItem);

        tracker = new StorageCommitmentTracker(
                "sopInstance-001",
                "seriesId-001",
                true,
                true,
                new Date(),
                "requestId-001"
        );

        request = new StorageCommitmentRequest("seriesId", true, List.of(tracker));
    }


    @Test
    void testOnDimseRQ_withValidDICOMStream_shouldProcessSuccessfully() throws Exception {

        when(kafkaTopicConfig.getStgcmt()).thenReturn("test-topic");

        Path tempPath = Files.createTempFile("dicom", ".dcm");
        File tempFile = tempPath.toFile();

        try (DicomOutputStream dos = new DicomOutputStream(tempFile)) {
            dos.writeDataset(baseActionInfo.createFileMetaInformation(UID.StorageCommitmentPushModel), baseActionInfo);
        }

        byte[] dicomBytes = Files.readAllBytes(tempFile.toPath());
        ByteArrayInputStream byteStream = new ByteArrayInputStream(dicomBytes);
        PDVInputStream pdvInputStream = mock(PDVInputStream.class);
        when(pdvInputStream.read(any(), anyInt(), anyInt()))
                .thenAnswer(invocation -> byteStream.read(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));

        DicomRequestDBObject mockRecord = new DicomRequestDBObject();
        mockRecord.setSeriesInstanceUid("1.2.3.4.5.6.7");
        when(databaseService.getDicomInstanceBySopInstanceUid("1.2.3.4.5.6"))
                .thenReturn(Optional.of(mockRecord));
        when(databaseService.saveStorageCommitment(any())).thenReturn("success");

        Attributes cmd = new Attributes();
        cmd.setInt(Tag.CommandField, VR.US, 0x0101);

        try (MockedStatic<StorageCommitmentUtils> utils = mockStatic(StorageCommitmentUtils.class)) {
            utils.when(() -> StorageCommitmentUtils.createSuccessItem(any(), any()))
                    .thenReturn(baseActionInfo);
            utils.when(() -> StorageCommitmentUtils.buildTracker(any(), any(), anyBoolean(), any()))
                    .thenReturn(tracker);
            utils.when(() -> StorageCommitmentUtils.buildCommitRequest(any(), any(), anyBoolean()))
                    .thenReturn(request);

            service.onDimseRQ(association, presentationContext, Dimse.N_ACTION_RQ, cmd, pdvInputStream);

            verify(databaseService).getDicomInstanceBySopInstanceUid("1.2.3.4.5.6");
            verify(databaseService).saveStorageCommitment(any());
            verify(eventNotificationService).sendEvent(any(), any(), any());
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    @Test
    void testOnDimseRQ_withUnsupportedDimse_shouldThrowException() {

        PDVInputStream pdvInputStream = mock(PDVInputStream.class);
        Attributes cmd = new Attributes();

        DicomServiceException exception = assertThrows(DicomServiceException.class, () ->
                service.onDimseRQ(association, presentationContext, Dimse.C_STORE_RQ, cmd, pdvInputStream)
        );

        assertEquals(Status.UnrecognizedOperation, exception.getStatus());
        assertEquals("Only N-ACTION is supported for Storage Commitment", exception.getMessage());
    }

    @Test
    void testOnDimseRQ_withMissingTransactionUID_shouldThrowDicomServiceException() throws Exception {

        Attributes invalidActionInfo = new Attributes(baseActionInfo);
        invalidActionInfo.remove(Tag.TransactionUID);

        Path tempPath = Files.createTempFile("invalid", ".dcm");
        File tempFile = tempPath.toFile();

        try (DicomOutputStream dos = new DicomOutputStream(tempFile)) {
            dos.writeDataset(invalidActionInfo.createFileMetaInformation(UID.StorageCommitmentPushModel), invalidActionInfo);
        }

        byte[] dicomBytes = Files.readAllBytes(tempFile.toPath());
        ByteArrayInputStream byteStream = new ByteArrayInputStream(dicomBytes);
        PDVInputStream pdvInputStream = mock(PDVInputStream.class);
        when(pdvInputStream.read(any(), anyInt(), anyInt()))
                .thenAnswer(invocation -> byteStream.read(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));

        Attributes cmd = new Attributes();
        cmd.setInt(Tag.CommandField, VR.US, 0x0101);

        DicomServiceException exception = assertThrows(DicomServiceException.class, () ->
                service.onDimseRQ(association, presentationContext, Dimse.N_ACTION_RQ, cmd, pdvInputStream)
        );

        assertEquals(Status.MissingAttributeValue, exception.getStatus());
        assertEquals("Missing Transaction UID or Referenced SOP Sequence", exception.getMessage());

        Files.deleteIfExists(tempFile.toPath());
    }

    @Test
    void testOnClose_shouldLogAndCallGetRemoteAET() {
        when(association.getRemoteAET()).thenReturn("REMOTE_AE");

        service.onClose(association);
        verify(association).getRemoteAET();
    }

    @Test
    void testGetSOPClasses_shouldReturnStorageCommitmentUID() {
        String[] sopClasses = service.getSOPClasses();

        assertNotNull(sopClasses);
        assertEquals(1, sopClasses.length);
        assertEquals(UID.StorageCommitmentPushModel, sopClasses[0]);
    }


//    @Test
//    void testOnDimseRQ_whenSOPInstanceUIDMissing_shouldSkipItemAndContinue() throws Exception {
//
//        Attributes actionInfo = new Attributes();
//        actionInfo.setString(Tag.TransactionUID, VR.UI, "1.2.3.4.5");
//
//        Sequence refSOPSeq = actionInfo.newSequence(Tag.ReferencedSOPSequence, 1);
//
//        // Item: SOPClassUID present but SOPInstanceUID missing → line 113 triggers
//        Attributes missingInstanceItem = new Attributes();
//        missingInstanceItem.setString(Tag.ReferencedSOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
//        // ReferencedSOPInstanceUID intentionally NOT set
//        refSOPSeq.add(missingInstanceItem);
//
//        Path tempPath = Files.createTempFile("dicom-null-instance", ".dcm");
//        File tempFile = tempPath.toFile();
//        try (DicomOutputStream dos = new DicomOutputStream(tempFile)) {
//            dos.writeDataset(
//                    actionInfo.createFileMetaInformation(UID.StorageCommitmentPushModel),
//                    actionInfo);
//        }
//
//        byte[] dicomBytes = Files.readAllBytes(tempFile.toPath());
//        ByteArrayInputStream byteStream = new ByteArrayInputStream(dicomBytes);
//        PDVInputStream pdvInputStream = mock(PDVInputStream.class);
//        when(pdvInputStream.read(any(), anyInt(), anyInt()))
//                .thenAnswer(inv -> byteStream.read(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
//
//        Attributes cmd = new Attributes();
//        cmd.setInt(Tag.CommandField, VR.US, 0x0101);
//
//        // trackers will be empty → persistStorageCommitment returns early
//        assertDoesNotThrow(() ->
//                service.onDimseRQ(association, presentationContext, Dimse.N_ACTION_RQ, cmd, pdvInputStream));
//
//        // No DB lookup should happen — all items were skipped
//        verify(databaseService, never()).getDicomInstanceBySopInstanceUid(any());
//
//        Files.deleteIfExists(tempFile.toPath());
//    }


    @Test
    void testOnDimseRQ_whenSOPClassUIDMissing_shouldSkipItemAndContinue() throws Exception {

        Attributes actionInfoWithNullSopClass = new Attributes();
        actionInfoWithNullSopClass.setString(Tag.TransactionUID, VR.UI, "1.2.3.4.5");
        // Required by createFileMetaInformation — NOT related to the sequence items
        actionInfoWithNullSopClass.setString(Tag.SOPClassUID, VR.UI, UID.StorageCommitmentPushModel);
        actionInfoWithNullSopClass.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5.meta");

        Sequence refSOPSeq = actionInfoWithNullSopClass.newSequence(Tag.ReferencedSOPSequence, 2);

        // Item 1: ReferencedSOPClassUID intentionally missing → triggers line 113 → continue
        Attributes missingClassItem = new Attributes();
        missingClassItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.3.4.5.6");
        refSOPSeq.add(missingClassItem);

        // Item 2: fully valid → processed normally
        Attributes validItem = new Attributes();
        validItem.setString(Tag.ReferencedSOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        validItem.setString(Tag.ReferencedSOPInstanceUID, VR.UI, "1.2.3.4.5.7");
        refSOPSeq.add(validItem);

        Path tempPath = Files.createTempFile("dicom-null-sop", ".dcm");
        File tempFile = tempPath.toFile();
        try (DicomOutputStream dos = new DicomOutputStream(tempFile)) {
            dos.writeDataset(
                    actionInfoWithNullSopClass.createFileMetaInformation(UID.StorageCommitmentPushModel),
                    actionInfoWithNullSopClass);
        }

        byte[] dicomBytes = Files.readAllBytes(tempFile.toPath());
        ByteArrayInputStream byteStream = new ByteArrayInputStream(dicomBytes);
        PDVInputStream pdvInputStream = mock(PDVInputStream.class);
        when(pdvInputStream.read(any(), anyInt(), anyInt()))
                .thenAnswer(inv -> byteStream.read(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

        DicomRequestDBObject mockRecord = new DicomRequestDBObject();
        mockRecord.setSeriesInstanceUid("series-001");
        // Only the valid item (1.2.3.4.5.7) should be queried — missing item skipped
        when(databaseService.getDicomInstanceBySopInstanceUid("1.2.3.4.5.7"))
                .thenReturn(Optional.of(mockRecord));
        when(databaseService.saveStorageCommitment(any())).thenReturn("success");
        when(kafkaTopicConfig.getStgcmt()).thenReturn("test-topic");

        Attributes cmd = new Attributes();
        cmd.setInt(Tag.CommandField, VR.US, 0x0101);

        try (MockedStatic<StorageCommitmentUtils> utils = mockStatic(StorageCommitmentUtils.class)) {
            utils.when(() -> StorageCommitmentUtils.createSuccessItem(any(), any()))
                    .thenReturn(new Attributes());
            utils.when(() -> StorageCommitmentUtils.buildTracker(any(), any(), anyBoolean(), any()))
                    .thenReturn(tracker);
            utils.when(() -> StorageCommitmentUtils.buildCommitRequest(any(), any(), anyBoolean()))
                    .thenReturn(request);

            assertDoesNotThrow(() ->
                    service.onDimseRQ(association, presentationContext, Dimse.N_ACTION_RQ, cmd, pdvInputStream));

            // Item 1 was skipped — its instance UID must never reach the DB
            verify(databaseService, never()).getDicomInstanceBySopInstanceUid("1.2.3.4.5.6");
            // Item 2 was processed normally
            verify(databaseService).getDicomInstanceBySopInstanceUid("1.2.3.4.5.7");
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    @Test
    void testOnDimseRQ_whenSOPInstanceUIDMissing_shouldSkipItemAndContinue() throws Exception {

        Attributes actionInfo = new Attributes();
        actionInfo.setString(Tag.TransactionUID, VR.UI, "1.2.3.4.5");
        // Required by createFileMetaInformation
        actionInfo.setString(Tag.SOPClassUID, VR.UI, UID.StorageCommitmentPushModel);
        actionInfo.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.5.meta");

        Sequence refSOPSeq = actionInfo.newSequence(Tag.ReferencedSOPSequence, 1);

        // Item: SOPClassUID present but ReferencedSOPInstanceUID missing → line 113 → continue
        Attributes missingInstanceItem = new Attributes();
        missingInstanceItem.setString(Tag.ReferencedSOPClassUID, VR.UI, "1.2.840.10008.5.1.4.1.1.2");
        // ReferencedSOPInstanceUID intentionally NOT set
        refSOPSeq.add(missingInstanceItem);

        Path tempPath = Files.createTempFile("dicom-null-instance", ".dcm");
        File tempFile = tempPath.toFile();
        try (DicomOutputStream dos = new DicomOutputStream(tempFile)) {
            dos.writeDataset(
                    actionInfo.createFileMetaInformation(UID.StorageCommitmentPushModel),
                    actionInfo);
        }

        byte[] dicomBytes = Files.readAllBytes(tempFile.toPath());
        ByteArrayInputStream byteStream = new ByteArrayInputStream(dicomBytes);
        PDVInputStream pdvInputStream = mock(PDVInputStream.class);
        when(pdvInputStream.read(any(), anyInt(), anyInt()))
                .thenAnswer(inv -> byteStream.read(
                        inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

        Attributes cmd = new Attributes();
        cmd.setInt(Tag.CommandField, VR.US, 0x0101);

        // All items skipped → trackers empty → persistStorageCommitment returns early
        assertDoesNotThrow(() ->
                service.onDimseRQ(association, presentationContext, Dimse.N_ACTION_RQ, cmd, pdvInputStream));

        // No DB lookup should happen — all items were skipped
        verify(databaseService, never()).getDicomInstanceBySopInstanceUid(any());

        Files.deleteIfExists(tempFile.toPath());
    }



    @Test
    void testOnDimseRQ_whenDicomInstanceNotFound_shouldAddToFailedSequence() throws Exception {

        when(kafkaTopicConfig.getStgcmt()).thenReturn("test-topic");

        Path tempPath = Files.createTempFile("dicom-notfound", ".dcm");
        File tempFile = tempPath.toFile();
        try (DicomOutputStream dos = new DicomOutputStream(tempFile)) {
            dos.writeDataset(
                    baseActionInfo.createFileMetaInformation(UID.StorageCommitmentPushModel),
                    baseActionInfo);
        }

        byte[] dicomBytes = Files.readAllBytes(tempFile.toPath());
        ByteArrayInputStream byteStream = new ByteArrayInputStream(dicomBytes);
        PDVInputStream pdvInputStream = mock(PDVInputStream.class);
        when(pdvInputStream.read(any(), anyInt(), anyInt()))
                .thenAnswer(inv -> byteStream.read(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

        // SOP instance NOT found in DB → found = false → failedSeq.add() on line 125
        when(databaseService.getDicomInstanceBySopInstanceUid("1.2.3.4.5.6"))
                .thenReturn(Optional.empty());
        when(databaseService.saveStorageCommitment(any())).thenReturn("success");

        Attributes cmd = new Attributes();
        cmd.setInt(Tag.CommandField, VR.US, 0x0101);

        StorageCommitmentTracker failedTracker = new StorageCommitmentTracker(
                "sopInstance-001", null, false, false, new Date(), "requestId-001"
        );
        StorageCommitmentRequest failedRequest = new StorageCommitmentRequest(null, false, List.of(failedTracker));

        try (MockedStatic<StorageCommitmentUtils> utils = mockStatic(StorageCommitmentUtils.class)) {
            utils.when(() -> StorageCommitmentUtils.createFailedItem(any(), any()))   // line 125
                    .thenReturn(new Attributes());
            utils.when(() -> StorageCommitmentUtils.buildTracker(any(), any(), anyBoolean(), any())) // lines 126-127
                    .thenReturn(failedTracker);
            utils.when(() -> StorageCommitmentUtils.buildCommitRequest(any(), any(), anyBoolean()))
                    .thenReturn(failedRequest);

            service.onDimseRQ(association, presentationContext, Dimse.N_ACTION_RQ, cmd, pdvInputStream);

            // createFailedItem must be called (line 125) — NOT createSuccessItem
            utils.verify(() -> StorageCommitmentUtils.createFailedItem(
                    eq("1.2.840.10008.5.1.4.1.1.2"), eq("1.2.3.4.5.6")));
            utils.verify(() -> StorageCommitmentUtils.createSuccessItem(any(), any()), never());

            // buildTracker called with found=false (lines 126-127)
            utils.verify(() -> StorageCommitmentUtils.buildTracker(
                    isNull(),           // seriesId is null when not found
                    eq("1.2.3.4.5.6"),
                    eq(false),          // found = false
                    eq("1.2.3.4.5")));

            verify(databaseService).saveStorageCommitment(any());
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

    // -----------------------------------------------------------------------
    // ADDITIONAL TEST: persistStorageCommitment when status != "success"
    // Covers: the else branch (log.warn) — Kafka NOT notified
    // -----------------------------------------------------------------------
    @Test
    void testOnDimseRQ_whenDbSaveFails_shouldNotSendKafkaEvent() throws Exception {

        Path tempPath = Files.createTempFile("dicom-dbfail", ".dcm");
        File tempFile = tempPath.toFile();
        try (DicomOutputStream dos = new DicomOutputStream(tempFile)) {
            dos.writeDataset(
                    baseActionInfo.createFileMetaInformation(UID.StorageCommitmentPushModel),
                    baseActionInfo);
        }

        byte[] dicomBytes = Files.readAllBytes(tempFile.toPath());
        ByteArrayInputStream byteStream = new ByteArrayInputStream(dicomBytes);
        PDVInputStream pdvInputStream = mock(PDVInputStream.class);
        when(pdvInputStream.read(any(), anyInt(), anyInt()))
                .thenAnswer(inv -> byteStream.read(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));

        DicomRequestDBObject mockRecord = new DicomRequestDBObject();
        mockRecord.setSeriesInstanceUid("series-001");
        when(databaseService.getDicomInstanceBySopInstanceUid("1.2.3.4.5.6"))
                .thenReturn(Optional.of(mockRecord));
        // DB save returns failure → Kafka must NOT be called
        when(databaseService.saveStorageCommitment(any())).thenReturn("failure");

        Attributes cmd = new Attributes();
        cmd.setInt(Tag.CommandField, VR.US, 0x0101);

        try (MockedStatic<StorageCommitmentUtils> utils = mockStatic(StorageCommitmentUtils.class)) {
            utils.when(() -> StorageCommitmentUtils.createSuccessItem(any(), any()))
                    .thenReturn(new Attributes());
            utils.when(() -> StorageCommitmentUtils.buildTracker(any(), any(), anyBoolean(), any()))
                    .thenReturn(tracker);
            utils.when(() -> StorageCommitmentUtils.buildCommitRequest(any(), any(), anyBoolean()))
                    .thenReturn(request);

            service.onDimseRQ(association, presentationContext, Dimse.N_ACTION_RQ, cmd, pdvInputStream);

            verify(eventNotificationService, never()).sendEvent(any(), any(), any());
        } finally {
            Files.deleteIfExists(tempFile.toPath());
        }
    }

}

