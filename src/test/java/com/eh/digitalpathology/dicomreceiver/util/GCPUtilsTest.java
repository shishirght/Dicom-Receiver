package com.eh.digitalpathology.dicomreceiver.util;

import com.eh.digitalpathology.dicomreceiver.config.GcpConfig;
import com.eh.digitalpathology.dicomreceiver.exceptions.HealthcareApiException;
import com.google.api.services.healthcare.v1.CloudHealthcareScopes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GCPUtilsTest {

    private GcpConfig gcpConfig;

    @BeforeEach
    void setUp() {
        gcpConfig = mock(GcpConfig.class);
    }

    @Test
    void testGetAccessToken_WhenCredentialsBlank_ShouldReturnEmptyString() {
        when(gcpConfig.getCreds()).thenReturn("   ");

        String token = GCPUtils.getAccessToken(gcpConfig);

        assertEquals("", token);
        verify(gcpConfig, times(1)).getCreds();
    }

    @Test
    void testGetAccessToken_WhenCredentialsValid_ShouldReturnMockedToken() throws Exception {
        when(gcpConfig.getCreds()).thenReturn("{\"mock\":\"json\"}");

        // Mock static method GoogleCredentials.fromStream()
        try (MockedStatic<GoogleCredentials> mockStatic = mockStatic(GoogleCredentials.class)) {

            GoogleCredentials mockCredentials = mock(GoogleCredentials.class);
            AccessToken mockAccessToken = mock(AccessToken.class);

            mockStatic.when(() -> GoogleCredentials.fromStream(any(ByteArrayInputStream.class)))
                    .thenReturn(mockCredentials);

            when(mockCredentials.createScoped(Collections.singleton(CloudHealthcareScopes.CLOUD_PLATFORM)))
                    .thenReturn(mockCredentials);
            doNothing().when(mockCredentials).refreshIfExpired();
            when(mockCredentials.refreshAccessToken()).thenReturn(mockAccessToken);
            when(mockAccessToken.getTokenValue()).thenReturn("MOCK_TOKEN_123");

            String token = GCPUtils.getAccessToken(gcpConfig);

            assertEquals("MOCK_TOKEN_123", token);
            verify(mockCredentials, times(1)).refreshIfExpired();
            verify(mockCredentials, times(1)).refreshAccessToken();
        }
    }

    @Test
    void testGetAccessToken_WhenExceptionOccurs_ShouldThrowHealthcareApiException() {
        when(gcpConfig.getCreds()).thenReturn("invalid-json");

        try (MockedStatic<GoogleCredentials> mockStatic = mockStatic(GoogleCredentials.class)) {
            mockStatic.when(() -> GoogleCredentials.fromStream(any(ByteArrayInputStream.class)))
                    .thenThrow(new RuntimeException("Mocked failure"));

            HealthcareApiException exception = assertThrows(
                    HealthcareApiException.class,
                    () -> GCPUtils.getAccessToken(gcpConfig)
            );

            assertTrue(exception.getMessage().contains("Failed to get access token"));
        }
    }
}
