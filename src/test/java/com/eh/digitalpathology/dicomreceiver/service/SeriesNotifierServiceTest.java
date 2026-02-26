package com.eh.digitalpathology.dicomreceiver.service;

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeriesNotifierServiceTest {

    @InjectMocks
    private SeriesNotifierService seriesNotifierService;

    @Mock
    private CloseableHttpClient mockHttpClient;

    @Mock
    private CloseableHttpResponse mockHttpResponse;

    @Mock
    private StatusLine mockStatusLine;

    @Mock
    private HttpEntity mockHttpEntity;

    private static final String VISIOPHARM_URL = "http://visiopharm.test/api/notify";
    private static final String SERIES_URL = "http://dicom.server/series/123";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(seriesNotifierService, "visiopharmUrl", VISIOPHARM_URL);
    }

    @Test
    void testNotifyVisiopharm_Success() throws Exception {
        // Arrange
        when(mockStatusLine.getStatusCode()).thenReturn(200);
        when(mockHttpResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream("Success".getBytes()));
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);

        try (MockedStatic<HttpClients> mockedHttpClients = mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockHttpClient);

            // Act
            seriesNotifierService.notifyVisiopharm(SERIES_URL);

            // Assert
            verify(mockHttpClient).execute(any(HttpPost.class));
            verify(mockHttpResponse).close();
            verify(mockHttpClient).close();
        }
    }

    @Test
    void testNotifyVisiopharm_ClientError400() throws Exception {
        // Arrange
        when(mockStatusLine.getStatusCode()).thenReturn(400);
        when(mockHttpResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream("Bad Request".getBytes()));
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);

        try (MockedStatic<HttpClients> mockedHttpClients = mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockHttpClient);

            // Act
            seriesNotifierService.notifyVisiopharm(SERIES_URL);

            // Assert
            verify(mockHttpClient).execute(any(HttpPost.class));
            verify(mockHttpResponse).close();
            verify(mockHttpClient).close();
        }
    }

    @Test
    void testNotifyVisiopharm_ClientError404() throws Exception {
        // Arrange
        when(mockStatusLine.getStatusCode()).thenReturn(404);
        when(mockHttpResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream("Not Found".getBytes()));
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);

        try (MockedStatic<HttpClients> mockedHttpClients = mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockHttpClient);

            // Act
            seriesNotifierService.notifyVisiopharm(SERIES_URL);

            // Assert
            verify(mockHttpClient).execute(any(HttpPost.class));
            verify(mockHttpResponse).close();
            verify(mockHttpClient).close();
        }
    }

    @Test
    void testNotifyVisiopharm_ServerError500() throws Exception {
        // Arrange
        when(mockStatusLine.getStatusCode()).thenReturn(500);
        when(mockHttpResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream("Internal Server Error".getBytes()));
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);

        try (MockedStatic<HttpClients> mockedHttpClients = mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockHttpClient);

            // Act
            seriesNotifierService.notifyVisiopharm(SERIES_URL);

            // Assert
            verify(mockHttpClient).execute(any(HttpPost.class));
            verify(mockHttpResponse).close();
            verify(mockHttpClient).close();
        }
    }

    @Test
    void testNotifyVisiopharm_ServerError503() throws Exception {
        // Arrange
        when(mockStatusLine.getStatusCode()).thenReturn(503);
        when(mockHttpResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream("Service Unavailable".getBytes()));
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);

        try (MockedStatic<HttpClients> mockedHttpClients = mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockHttpClient);

            // Act
            seriesNotifierService.notifyVisiopharm(SERIES_URL);

            // Assert
            verify(mockHttpClient).execute(any(HttpPost.class));
            verify(mockHttpResponse).close();
            verify(mockHttpClient).close();
        }
    }

    @Test
    void testNotifyVisiopharm_IOException() throws Exception {
        // Arrange
        when(mockHttpClient.execute(any(HttpPost.class))).thenThrow(new IOException("Connection timeout"));

        try (MockedStatic<HttpClients> mockedHttpClients = mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockHttpClient);

            // Act
            seriesNotifierService.notifyVisiopharm(SERIES_URL);

            // Assert
            verify(mockHttpClient).execute(any(HttpPost.class));
            verify(mockHttpClient).close();
        }
    }

    @Test
    void testNotifyVisiopharm_GenericException() throws Exception {
        // Arrange
        when(mockHttpClient.execute(any(HttpPost.class))).thenThrow(new RuntimeException("Unexpected error"));

        try (MockedStatic<HttpClients> mockedHttpClients = mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockHttpClient);

            // Act
            seriesNotifierService.notifyVisiopharm(SERIES_URL);

            // Assert
            verify(mockHttpClient).execute(any(HttpPost.class));
            verify(mockHttpClient).close();
        }
    }

    @Test
    void testNotifyVisiopharm_VerifyRequestHeaders() throws Exception {
        // Arrange
        when(mockStatusLine.getStatusCode()).thenReturn(200);
        when(mockHttpResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockHttpEntity.getContent()).thenReturn(new ByteArrayInputStream("Success".getBytes()));
        when(mockHttpResponse.getEntity()).thenReturn(mockHttpEntity);
        when(mockHttpClient.execute(any(HttpPost.class))).thenReturn(mockHttpResponse);

        try (MockedStatic<HttpClients> mockedHttpClients = mockStatic(HttpClients.class)) {
            mockedHttpClients.when(HttpClients::createDefault).thenReturn(mockHttpClient);

            // Act
            seriesNotifierService.notifyVisiopharm(SERIES_URL);

            // Assert - verify the HttpPost was created with correct URL and headers
            verify(mockHttpClient).execute(argThat(request -> {
                HttpPost post = (HttpPost) request;
                return post.getURI().toString().equals(VISIOPHARM_URL) &&
                       post.getFirstHeader("Content-Type").getValue().equals("application/json");
            }));
        }
    }
}
