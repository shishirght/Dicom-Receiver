package com.eh.digitalpathology.dicomreceiver.service.storescp;

import com.eh.digitalpathology.dicomreceiver.util.CommonUtils;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DicomSCPServerTest {

    private DicomSCPServer dicomSCPServer;

    @BeforeEach
    void setUp() {
        StorageCommitmentSCPService storageCommitmentSCPService = mock(StorageCommitmentSCPService.class);
        DicomStoreSCPService dicomStoreSCPService = mock(DicomStoreSCPService.class);
        CommonUtils commonUtils = mock(CommonUtils.class);

        when(dicomStoreSCPService.getSOPClasses()).thenReturn(new String[]{"1.2.840.10008.1.1"});
        when(storageCommitmentSCPService.getSOPClasses()).thenReturn(new String[]{"1.2.840.10008.1.20.1"});
        when(commonUtils.getAeName()).thenReturn("TEST_AE");
        when(commonUtils.getPort()).thenReturn(11112);

        dicomSCPServer = new DicomSCPServer(storageCommitmentSCPService, dicomStoreSCPService, commonUtils);
    }

    @Test
    void testInitSetsAeTitleAndPort() throws NoSuchFieldException, IllegalAccessException {

        dicomSCPServer.init();

        Field aeField = DicomSCPServer.class.getDeclaredField("ae");
        aeField.setAccessible(true);
        ApplicationEntity ae = (ApplicationEntity) aeField.get(dicomSCPServer);
        assertEquals("TEST_AE", ae.getAETitle());

        Field connField = DicomSCPServer.class.getDeclaredField("conn");
        connField.setAccessible(true);
        Connection conn = (Connection) connField.get(dicomSCPServer);
        assertEquals(11112, conn.getPort());

    }

    @Test
    void testStartBindsConnections() throws IOException, GeneralSecurityException, NoSuchFieldException, IllegalAccessException {
        dicomSCPServer.init();

        Field deviceField = DicomSCPServer.class.getDeclaredField("device");
        deviceField.setAccessible(true);
        Device mockDevice = mock(Device.class);
        deviceField.set(dicomSCPServer, mockDevice);

        dicomSCPServer.start();

        verify(mockDevice, times(1)).bindConnections();
    }
}
