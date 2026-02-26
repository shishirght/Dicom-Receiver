package com.eh.digitalpathology.dicomreceiver.util;


import com.eh.digitalpathology.dicomreceiver.config.GcpConfig;
import com.eh.digitalpathology.dicomreceiver.exceptions.HealthcareApiException;
import com.google.api.services.healthcare.v1.CloudHealthcareScopes;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class GCPUtils {
    private GCPUtils ( ) {
    }

    public static String getAccessToken ( GcpConfig gcpConfig ) throws HealthcareApiException {
        String credentialsFilePath = gcpConfig.getCreds( );

        if ( credentialsFilePath == null || credentialsFilePath.isBlank( ) ) {
            return ""; // No credentials provided
        }
        try ( InputStream serviceAccountStream = new ByteArrayInputStream( credentialsFilePath.getBytes( StandardCharsets.UTF_8 ) ) ) {
            GoogleCredentials credentials = GoogleCredentials.fromStream( serviceAccountStream ).createScoped( Collections.singleton( CloudHealthcareScopes.CLOUD_PLATFORM ) );

            // Refresh only if the token is expired
            credentials.refreshIfExpired( );
            return credentials.refreshAccessToken( ).getTokenValue( );
        } catch ( Exception e ) {
            throw new HealthcareApiException( "Failed to get access token from service account credentials", e );
        }
    }

}
