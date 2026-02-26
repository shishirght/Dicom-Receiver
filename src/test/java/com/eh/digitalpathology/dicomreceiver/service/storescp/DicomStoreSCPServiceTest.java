package com.eh.digitalpathology.dicomreceiver.service.storescp;

import com.eh.digitalpathology.dicomreceiver.util.CommonUtils;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.pdu.PresentationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DicomStoreSCPServiceTest  {

    private CommonUtils commonUtils;
    private DicomStoreSCPService dicomStoreSCPService;

    @BeforeEach
    void setUp() {
        commonUtils = mock(CommonUtils.class);
        dicomStoreSCPService = new DicomStoreSCPService(commonUtils);
    }

    @Test
    void testStoreWritesDicomFile() throws Exception {
        String sopInstanceUID = "1.2.345.6666";
        String sopClassUID = UID.SecondaryCaptureImageStorage;
        String transferSyntaxUID = UID.ImplicitVRLittleEndian;

        Path tempDir = Files.createTempDirectory("dicom-test");
        when(commonUtils.getLocalStoragePath()).thenReturn(tempDir.toString());

        Association association = mock(Association.class);
        PresentationContext pc = mock(PresentationContext.class);
        when(pc.getTransferSyntax()).thenReturn(transferSyntaxUID);

        Attributes rq = new Attributes();
        rq.setString(Tag.SOPClassUID, VR.UI, sopClassUID);
        rq.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUID);
        rq.setString(Tag.AffectedSOPInstanceUID, VR.UI, sopInstanceUID);
        Attributes rsp = new Attributes();


        Attributes dataset = new Attributes();
        dataset.setString(Tag.SOPClassUID, VR.UI, sopClassUID);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, sopInstanceUID);
        dataset.setString(Tag.PatientName, VR.PN, "Test^Patient");


        Path dicomTempFile = Files.createTempFile("test-dataset", ".dcm");
        try (DicomOutputStream dos = new DicomOutputStream(dicomTempFile.toFile())) {
            dos.writeDataset(dataset.createFileMetaInformation(transferSyntaxUID), dataset);
        }


        byte[] dicomBytes = Files.readAllBytes(dicomTempFile);
        ByteArrayInputStream dicomInputStream = new ByteArrayInputStream(dicomBytes);
        PDVInputStream pdvInputStream = mock(PDVInputStream.class);
        when(pdvInputStream.read(any(byte[].class), anyInt(), anyInt()))
                .thenAnswer(inv -> dicomInputStream.read(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));


        dicomStoreSCPService.store(association, pc, rq, pdvInputStream, rsp);

        Path expectedFile = tempDir.resolve(sopInstanceUID + ".dcm");
        assertTrue(Files.exists(expectedFile));

        Files.deleteIfExists(expectedFile);
        Files.deleteIfExists(dicomTempFile);
        Files.deleteIfExists(tempDir);
    }

}