package com.eh.digitalpathology.dicomreceiver.service;

import static org.mockito.Mockito.*;
import com.eh.digitalpathology.dicomreceiver.service.storescp.DicomSCPServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.IOException;
import java.security.GeneralSecurityException;

@ExtendWith(MockitoExtension.class)
class DicomSCPServerRunnerTest {

    @Mock
    private DicomSCPServer dicomSCPServer;
    @InjectMocks
    private DicomSCPServerRunner runner;

    @BeforeEach
    void setup() {
        dicomSCPServer = mock(DicomSCPServer.class);
        runner = new DicomSCPServerRunner(dicomSCPServer);
    }

    @Test
    void testStartDicomServer_ShouldStartSuccessfully() throws Exception {
        runner.startDicomServer();
        verify(dicomSCPServer, timeout(200).times(1)).start();
    }

    @Test
    void testStartDicomServer_ShouldLogError_WhenIOExceptionThrown() throws Exception {
        doThrow(new IOException("Connection refused")).when(dicomSCPServer).start();

        runner.startDicomServer();

        // Allow the spawned thread to execute and hit the catch block
        verify(dicomSCPServer, timeout(500).times(1)).start();
    }

    @Test
    void testStartDicomServer_ShouldLogError_WhenGeneralSecurityExceptionThrown() throws Exception {
        doThrow(new GeneralSecurityException("SSL failure")).when(dicomSCPServer).start();

        runner.startDicomServer();

        verify(dicomSCPServer, timeout(500).times(1)).start();
    }


}
