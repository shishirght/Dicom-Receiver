/**
 * DicomHealthcareApiClient class acts as a client for the DICOM Healthcare API, encapsulating
 * the logic for API calls to DICOM stores. It provides a reusable and
 * modular approach for accessing healthcare DICOM data while managing
 * errors through custom exceptions.
 * Copyright (c) 2025 [Your Company Name]. All rights reserved.
 * Author: Pooja Kamble
 * Date: October 30, 2024
 */

package com.eh.digitalpathology.dicomreceiver.api;


import com.eh.digitalpathology.dicomreceiver.config.GcpConfig;
import com.eh.digitalpathology.dicomreceiver.exceptions.HealthcareApiException;
import com.eh.digitalpathology.dicomreceiver.util.GCPUtils;
import jakarta.annotation.PreDestroy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.*;

@Component
public class DicomHealthcareApiClient {
    private static final Logger logger = LoggerFactory.getLogger( DicomHealthcareApiClient.class );
    private final GcpConfig gcpConfig;
    ExecutorService executorService = Executors.newFixedThreadPool( Runtime.getRuntime( ).availableProcessors( ) );

    public DicomHealthcareApiClient ( GcpConfig gcpConfig ) {
        this.gcpConfig = gcpConfig;
    }

    /**
     * This method is used to store dicom instances to final dicom store
     *
     * @param dicomFileMetadata contains file metadata information of dicom instance
     * @param dcmAttributes     contains tag data of dicom instance
     */
    public void storeDicomInstances ( Attributes dicomFileMetadata, Attributes dcmAttributes, String dicomUrl ) {
        String sopInstanceUid = dcmAttributes.getString( Tag.SOPInstanceUID );
        logger.info( "storeDicomInstances :: Starting the process of storing DICOM instance with SOPInstanceUID : {}", sopInstanceUid );
        try ( CloseableHttpClient httpClient = HttpClients.custom( ).setDefaultRequestConfig( RequestConfig.custom( ).setSocketTimeout( 120000 ).setConnectTimeout( 120000 ).setConnectionRequestTimeout( 120000 ).build( ) ).build( ); PipedInputStream pipedInputStream = new PipedInputStream( 1048576 ); PipedOutputStream pipedOutputStream = new PipedOutputStream( pipedInputStream ) ) {
            String uri = String.format( "%s/%s/dicomWeb/studies", gcpConfig.getDicomWebUrl( ), dicomUrl );
            logger.info( "storeDicomInstances :: Constructed DICOM Store URI: {}", uri );

            String accessToken = GCPUtils.getAccessToken( gcpConfig );
            logger.debug( "storeDicomInstances :: Submitting a task to write DICOM data to the output stream." );

            Future< ? > writeTask = executorService.submit( ( ) -> {
                try ( DicomOutputStream dicomOutputStream = new DicomOutputStream( pipedOutputStream, "" ) ) {
                    dicomOutputStream.writeDataset( dicomFileMetadata, dcmAttributes );
                } catch ( IOException ex ) {
                    throw new HealthcareApiException( "Failed to write DICOM data to the output stream", ex );
                }
            } );
            HttpPost httpPost = new HttpPost( uri );
            httpPost.setHeader( HttpHeaders.CONTENT_TYPE, "application/dicom" );

            if ( accessToken != null && !accessToken.isEmpty( ) ) {
                httpPost.setHeader( HttpHeaders.AUTHORIZATION, "Bearer " + accessToken );
            }

            HttpEntity entity = new InputStreamEntity( pipedInputStream );
            httpPost.setEntity( entity );

            storeInstances( writeTask, httpClient, httpPost, sopInstanceUid );
        } catch ( Exception e ) {
            String errorMessage = String.format( "An error occurred while uploading the DICOM file with SOPInstanceUID '%s' to the final DICOM store during the STOW-RS operation.", sopInstanceUid );
            throw new HealthcareApiException( errorMessage, e );
        }
    }

    private void storeInstances ( Future< ? > writeTask, CloseableHttpClient httpClient, HttpPost httpPost, String sopInstanceUid ) {
        try ( CloseableHttpResponse response = httpClient.execute( httpPost ) ) {
            int statusCode = response.getStatusLine( ).getStatusCode( );
            if ( statusCode != HttpStatus.SC_OK ) {
                writeTask.cancel( true );
                String errorMessage = String.format( "Error storing DICOM instance with SOPInstance id %s: %s - %s", sopInstanceUid, statusCode, response.getStatusLine( ).getReasonPhrase( ) );
                throw new HealthcareApiException( errorMessage );
            }

            handleWriteTask( writeTask );

            logger.info( "storeInstances :: DICOM file with SOPInstanceUID '{}' successfully uploaded to the final DICOM store.", sopInstanceUid );
        } catch ( IOException ex ) {
            String errorMessage = String.format( "Failed to send DICOM data for SOPInstanceUID '%s' to the final DICOM store during the STOW-RS operation.", sopInstanceUid );
            throw new HealthcareApiException( errorMessage, ex );
        }
    }

    private void handleWriteTask ( Future< ? > writeTask ) {
        try {
            writeTask.get( 5, TimeUnit.MINUTES );
        } catch ( InterruptedException | ExecutionException e ) {
            Thread.currentThread( ).interrupt( );
            logger.error( "handleWriteTask :: Write task was interrupted", e );
        } catch ( TimeoutException e ) {
            writeTask.cancel( true );
            logger.error( "handleWriteTask :: Write task was timed out", e );
        }
    }

    @PreDestroy
    public void shutdownExecutor ( ) {
        executorService.shutdown( );
        try {
            if ( !executorService.awaitTermination( 30, TimeUnit.SECONDS ) ) {
                executorService.shutdownNow( );
            }
        } catch ( InterruptedException ex ) {
            executorService.shutdownNow( );
            Thread.currentThread( ).interrupt( );
        }

    }
}