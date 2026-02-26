package com.eh.digitalpathology.dicomreceiver.service;

import static org.mockito.Mockito.*;
import com.eh.digitalpathology.dicomreceiver.service.storescp.DicomSCPServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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


}
