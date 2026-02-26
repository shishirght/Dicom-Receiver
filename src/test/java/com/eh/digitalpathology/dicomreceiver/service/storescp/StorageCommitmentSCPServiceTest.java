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

}

