package com.eh.digitalpathology.dicomreceiver.api;

import com.eh.digitalpathology.dicomreceiver.config.GcpConfig;
import com.eh.digitalpathology.dicomreceiver.exceptions.HealthcareApiException;
import com.eh.digitalpathology.dicomreceiver.util.GCPUtils;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DicomHealthcareApiClientTest {

    @Mock
    private GcpConfig gcpConfig;

    @Mock
    private CloseableHttpClient mockHttpClient;

    @Mock
    private CloseableHttpResponse mockResponse;

    @Mock
    private StatusLine mockStatusLine;

    @Spy
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    private DicomHealthcareApiClient client;

    @BeforeEach
    void setUp() {
        client = new DicomHealthcareApiClient(gcpConfig);
        client.executorService = executorService;
    }

    @AfterEach
    void tearDown() {
        client.shutdownExecutor();
        executorService.shutdownNow();
    }


    @Test
    void testStoreDicomInstances_SuccessfulUpload() throws Exception {

        Attributes meta = new Attributes();
        meta.setString(Tag.MediaStorageSOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        meta.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, "SOP123");
        meta.setString(Tag.TransferSyntaxUID, VR.UI, UID.ImplicitVRLittleEndian);

        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPClassUID, VR.UI, UID.SecondaryCaptureImageStorage);
        attrs.setString(Tag.SOPInstanceUID, VR.UI, "SOP123");
        attrs.setString(Tag.PatientName, VR.PN, "Test^Patient");

        when(gcpConfig.getDicomWebUrl()).thenReturn("http://test-url");
        when(gcpConfig.getPathqaStoreUrl()).thenReturn("storePath");

        try (MockedStatic<HttpClients> mockedHttpClients = mockStatic(HttpClients.class);
                MockedStatic<GCPUtils> mockedGCPUtils = mockStatic(GCPUtils.class);
                MockedConstruction<DicomOutputStream> mockedDicomOutputStream =
                        mockConstruction(DicomOutputStream.class, (mock, context) -> {
                            doNothing().when(mock).writeDataset(any(), any());
                            doNothing().when(mock).close();})) {
            mockedGCPUtils.when(() -> GCPUtils.getAccessToken(any())).thenReturn("test-token");

            HttpClientBuilder mockBuilder = mock(HttpClientBuilder.class);
            when(mockBuilder.setDefaultRequestConfig(any())).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockHttpClient);
            mockedHttpClients.when(HttpClients::custom).thenReturn(mockBuilder);

            when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);
            when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
            when(mockStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);

            assertDoesNotThrow(() -> client.storeDicomInstances(meta, attrs, gcpConfig.getPathqaStoreUrl()));

            verify(mockHttpClient, times(1)).execute(any(HttpPost.class));
            assertEquals("http://test-url", gcpConfig.getDicomWebUrl());
            assertEquals("storePath", gcpConfig.getPathqaStoreUrl());

        }
    }

    @Test
    void testStoreDicomInstances_WhenHttpFails_ShouldThrowException() throws Exception {

        Attributes meta = new Attributes();
        Attributes attrs = new Attributes();
        attrs.setString(Tag.SOPInstanceUID, VR.UI, "SOP123");

        when(gcpConfig.getDicomWebUrl()).thenReturn("http://test-url");
        when(gcpConfig.getPathqaStoreUrl()).thenReturn("storePath");

        try (
                MockedStatic<HttpClients> mockedHttpClients = mockStatic(HttpClients.class);
                MockedStatic<GCPUtils> mockedGCPUtils = mockStatic(GCPUtils.class)
        ) {
            mockedGCPUtils.when(() -> GCPUtils.getAccessToken(any())).thenReturn("test-token");

            HttpClientBuilder mockBuilder = mock(HttpClientBuilder.class);
            when(mockBuilder.setDefaultRequestConfig(any())).thenReturn(mockBuilder);
            when(mockBuilder.build()).thenReturn(mockHttpClient);
            mockedHttpClients.when(HttpClients::custom).thenReturn(mockBuilder);

            when(mockHttpClient.execute(any(HttpPost.class))).thenThrow(new IOException("error"));

            HealthcareApiException ex = assertThrows(HealthcareApiException.class,
                    () -> client.storeDicomInstances(meta, attrs, gcpConfig.getPathqaStoreUrl()));

            assertTrue(ex.getMessage().contains("An error occurred while uploading"));
        }
    }


    @Test
    void testShutdownExecutor_WhenNotTerminated_ShouldInvokeShutdownNow() throws InterruptedException {

        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(mockExecutor.awaitTermination(anyLong(), any())).thenReturn(false);
        client.executorService = mockExecutor;

        client.shutdownExecutor();
        verify(mockExecutor).shutdown();
        verify(mockExecutor).awaitTermination(30, TimeUnit.SECONDS);
        verify(mockExecutor).shutdownNow();
    }

    @Test
    void testShutdownExecutor_WhenInterrupted_ShouldInvokeShutdownNow() throws InterruptedException {

        ExecutorService mockExecutor = mock(ExecutorService.class);
        when(mockExecutor.awaitTermination(anyLong(), any())).thenThrow(new InterruptedException());
        client.executorService = mockExecutor;
        boolean wasInterruptedBefore = Thread.currentThread().isInterrupted();

        client.shutdownExecutor();

        verify(mockExecutor).shutdown();
        verify(mockExecutor).shutdownNow();
        assertTrue(Thread.interrupted());
        if (wasInterruptedBefore) Thread.currentThread().interrupt();
    }

    @Test
    void testStoreInstances_ShouldThrowHealthcareApiException() throws Exception {
        Future<?> mockFuture = mock(Future.class);
        HttpPost mockPost = new HttpPost("http://test");

        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockResponse);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(500);
        when(mockStatusLine.getReasonPhrase()).thenReturn("Internal Server Error");

        HealthcareApiException ex = assertThrows(HealthcareApiException.class,
                () -> ReflectionTestUtils.invokeMethod(client, "storeInstances", mockFuture, mockHttpClient, mockPost, "SOP123"));

        assertTrue(ex.getMessage().contains("Error storing DICOM instance with SOPInstance id"));
        verify(mockFuture).cancel(true);
    }



    @Test
    void testHandleWriteTask_WhenFutureThrowsExecutionException_ShouldInterruptThread() throws Exception {
        Future<Object> mockFuture = mock(Future.class); // Use Future<Object> not Future<?>

        when(mockFuture.get(5L, TimeUnit.MINUTES))  // Match exact args from source
                .thenThrow(new ExecutionException("Write failed", new RuntimeException("cause")));

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(client, "handleWriteTask", (Object) mockFuture));

        assertTrue(Thread.interrupted()); // clears flag too, keeping tests isolated
    }


    @Test
    void testHandleWriteTask_WhenFutureThrowsInterruptedException_ShouldInterruptThread() throws Exception {
        Future<Object> mockFuture = mock(Future.class);

        // Use lenient stubbing to avoid strict mismatch errors
        lenient().when(mockFuture.get(eq(5L), any(TimeUnit.class)))
                .thenThrow(new InterruptedException("Interrupted"));

        // Run the method under test
        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(client, "handleWriteTask", (Object) mockFuture));

        // Check that the thread was interrupted (without clearing the flag)
        assertTrue(Thread.currentThread().isInterrupted(),
                "Thread should be interrupted after InterruptedException");
    }


    @Test
    void testHandleWriteTask_WhenFutureTimesOut_ShouldCancelTask() throws Exception {
        Future<Object> mockFuture = mock(Future.class);

        when(mockFuture.get(5L, TimeUnit.MINUTES))
                .thenThrow(new TimeoutException("Timed out"));

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(client, "handleWriteTask", (Object) mockFuture));

        verify(mockFuture).cancel(true);
    }

}
