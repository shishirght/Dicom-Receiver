package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.api.DicomHealthcareApiClient;
import com.eh.digitalpathology.dicomreceiver.config.GcpConfig;
import com.eh.digitalpathology.dicomreceiver.config.KafkaTopicConfig;
import com.eh.digitalpathology.dicomreceiver.constants.WatchDirectoryConstant;
import com.eh.digitalpathology.dicomreceiver.exceptions.DicomAttributesException;
import com.eh.digitalpathology.dicomreceiver.model.DicomDirDocument;
import com.eh.digitalpathology.dicomreceiver.model.DicomRequestDBObject;
import com.eh.digitalpathology.dicomreceiver.model.SlideScanner;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.cloud.ServiceOptions;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


@Service
@RefreshScope
public class DicomExtractorService {
    private static final Logger log = LoggerFactory.getLogger( DicomExtractorService.class.getName( ) );

    @Value( "${dicom.barcode.generation.enable}" )
    private boolean enableBarcodeGeneration;


    private final EventNotificationService eventNotificationService;
    private final DatabaseService dbService;
    private final DicomHealthcareApiClient dicomHealthcareApiClient;
    private final DicomDirService dicomDirService;
    private final Cache< String, String > studyBarcodeCache;
    private final BarcodeUploadTrackerService barcodeUploadTrackerService;
    private final KafkaTopicConfig kafkaTopicConfig;
    private final GcpConfig gcpConfig;
    private final SeriesUploadTrackerService seriesUploadTrackerService;

    public DicomExtractorService ( EventNotificationService eventNotificationService, DatabaseService dbService, DicomHealthcareApiClient dicomHealthcareApiClient, DicomDirService dicomDirService, Cache< String, String > studyBarcodeCache, BarcodeUploadTrackerService barcodeUploadTrackerService, KafkaTopicConfig kafkaTopicConfig, GcpConfig gcpConfig, SeriesUploadTrackerService seriesUploadTrackerService ) {
        this.eventNotificationService = eventNotificationService;
        this.dbService = dbService;
        this.dicomHealthcareApiClient = dicomHealthcareApiClient;
        this.dicomDirService = dicomDirService;
        this.studyBarcodeCache = studyBarcodeCache;
        this.barcodeUploadTrackerService = barcodeUploadTrackerService;
        this.kafkaTopicConfig = kafkaTopicConfig;
        this.gcpConfig = gcpConfig;
        this.seriesUploadTrackerService = seriesUploadTrackerService;
    }

    public DicomRequestDBObject extract ( String finalPath, Path fileName ) throws DicomAttributesException {
        DicomRequestDBObject dicomRequestDBObject = new DicomRequestDBObject( );
        File dicomFile = new File( fileName.toString( ) );
        boolean isDicomDir = false;

        try ( DicomInputStream dicomInputStream = new DicomInputStream( dicomFile ) ) {
            log.info( "extract:: ************ Started extracting file : {}", dicomFile );
            // Read the DICOM dataset
            Attributes attributes = dicomInputStream.readDataset( );
            Attributes fmi = dicomInputStream.readFileMetaInformation( );
            log.info( "extract :: filename:: {}", fileName.getFileName( ) );

            String sopClassUID = attributes.getString( Tag.SOPClassUID );
            log.info( "extract :: attributes sop class UID :: {}", sopClassUID != null ? sopClassUID : "N/A" );
            isDicomDir = "1.2.840.10008.1.3.10".equals( fmi.getString( Tag.MediaStorageSOPClassUID ) ) || fileName.getFileName( ).toString( ).contains( "DICOMDIR" );

            if ( isDicomDir ) {
                log.info( "extract:: Processing DICOMDIR file: {}", dicomFile.getName( ) );
                processDicomDirWithDelay( fileName, attributes );
                return null; // Skip processing for DICOMDIR files
            }
            String seriesInstanceUID = attributes.getString( Tag.SeriesInstanceUID );
            String sopInstanceUID = attributes.getString( Tag.SOPInstanceUID );
            String studyInstanceUID = attributes.getString( Tag.StudyInstanceUID );
            String deviceSerialNumber = attributes.getString( Tag.DeviceSerialNumber );

            SlideScanner slideScanner = dbService.fetchScannerByDeviceSerialNumber( deviceSerialNumber );

            checkAttributeNull( dicomRequestDBObject, sopInstanceUID, "SOPInstanceUID" );
            checkAttributeNull( dicomRequestDBObject, seriesInstanceUID, "SeriesInstanceUID" );
            checkAttributeNull( dicomRequestDBObject, studyInstanceUID, "StudyInstanceUID" );
            log.info( "slide scanner ::: {}", slideScanner  );
            if ( slideScanner == null || !slideScanner.connected( ) ) {
                moveFileToTempStore( finalPath, dicomFile, sopInstanceUID, studyInstanceUID, seriesInstanceUID, deviceSerialNumber );
                return null;
            }
            log.info( "slide scanner connected:: {}", slideScanner.connected());
            log.info("slide scanner research:: {}", slideScanner.research());

            if (  attributes.getString( Tag.BarcodeValue ) == null && enableBarcodeGeneration ) {
                String barcodeValue = generateShortBarcode( studyInstanceUID, seriesInstanceUID );
                attributes.setString( Tag.BarcodeValue, VR.LO, barcodeValue );
                log.info( "extract :: Generated and set new barcode: {}", barcodeValue );
            }
            String barcodeValue = attributes.getString( Tag.BarcodeValue );
            if ( slideScanner.research( ) ) {
                dicomHealthcareApiClient.storeDicomInstances( fmi, attributes, gcpConfig.getResearchStoreUrl( ) );
                seriesUploadTrackerService.recordUpload( studyInstanceUID, seriesInstanceUID );
                return null;
            }
            checkAttributeNull( dicomRequestDBObject, barcodeValue, WatchDirectoryConstant.BARCODE );

            if ( studyInstanceUID != null && barcodeValue != null ) {
                studyBarcodeCache.put( studyInstanceUID, barcodeValue );
            }
            if (  dbService.isBarcodeExists( barcodeValue )  ) {
                dicomHealthcareApiClient.storeDicomInstances( fmi, attributes, gcpConfig.getPathqaStoreUrl( ) );
                barcodeUploadTrackerService.recordUpload( barcodeValue, studyInstanceUID, seriesInstanceUID, deviceSerialNumber );
                return null;
            }
            String path = moveFileToTempStore( finalPath, dicomFile, sopInstanceUID, studyInstanceUID, seriesInstanceUID, null );
            dicomRequestDBObject.setIntermediateStoragePath( path );
            dicomRequestDBObject.setDeviceSerialNumber( deviceSerialNumber );
            log.info( "extract :: Created DB object to save: {}", dicomRequestDBObject );
            // Get the current timestamp in ISO 8601 format
            dicomRequestDBObject.setDicomInstanceReceivedTimestamp( Calendar.getInstance( ).getTime( ) );


        } catch ( IOException e ) {
            throw new DicomAttributesException( "BAD_REQUEST", e.getMessage( ) );
        } finally {
            try {
                if ( !isDicomDir ) {
                    Files.deleteIfExists( dicomFile.toPath( ) );
                }
            } catch ( IOException e ) {
                log.error( "extract :: Unable to delete file : {}", e.getMessage( ) );
            }
        }
        log.info( "extract::  Extracting and moving file done for {}", dicomFile.getName( ) );
        return dicomRequestDBObject;
    }


    private void processDicomDirWithDelay ( Path dicomDirPath, Attributes attributes ) {
        try {
            DicomDirDocument dicomDirDocument = dicomDirService.fetchMetaData( dicomDirPath, attributes );
            if ( dicomDirDocument == null ) {
                log.warn( "processDicomDirWithDelay :: DicomDirDocument is null for path: {}", dicomDirPath );
                return;
            }
            String barcode = studyBarcodeCache.getIfPresent( dicomDirDocument.studyId( ) );

            if ( barcode != null ) {
                if ( dbService.isBarcodeExists( barcode )  ) {
                    log.info( "processDicomDirWithDelay :: Skipping DICOMDIR - barcode already exists in DB: {} (studyUID: {})", barcode, dicomDirDocument.studyId( ) );
                    Files.deleteIfExists( dicomDirPath );
                    return;
                } else {
                    log.info( "processDicomDirWithDelay :: Saving DICOMDIR immediately - barcode not in DB: {} (studyUID: {})", barcode, dicomDirDocument.studyId( ) );
                    dicomDirService.fetchAndStoreMetaData( dicomDirPath, dicomDirDocument );
                    return;
                }
            }
            // Barcode not in cache — wait and retry
            CompletableFuture.runAsync( ( ) -> {
                try {
                    Thread.sleep( 120_000 ); // Wait 2 minutes
                    String delayedBarcode = studyBarcodeCache.getIfPresent( dicomDirDocument.studyId( ) );
                    if ( delayedBarcode != null && dbService.isBarcodeExists( delayedBarcode ) ) {
                        log.info( "processDicomDirWithDelay :: Skipping DICOMDIR after delay - barcode exists: {} (studyUID: {})", delayedBarcode, dicomDirDocument.studyId( ) );
                        Files.deleteIfExists( dicomDirPath );
                        return;
                    }
                    if ( delayedBarcode == null ) {
                        log.info( "processDicomDirWithDelay :: Barcode still not found in cache after delay for studyUID: {}", dicomDirDocument.studyId( ) );
                        Files.deleteIfExists( dicomDirPath );
                        return;
                    }
                    log.info( "processDicomDirWithDelay :: Saving DICOMDIR after delay - barcode found in DB (studyUID: {})", dicomDirDocument.studyId( ) );
                    dicomDirService.fetchAndStoreMetaData( dicomDirPath, dicomDirDocument );
                } catch ( Exception e ) {
                    Thread.currentThread( ).interrupt( );
                    log.error( "processDicomDirWithDelay :: Error processing DICOMDIR after delay", e );
                }
            }, CompletableFuture.delayedExecutor( 2, TimeUnit.MINUTES ) );

        } catch ( Exception e ) {
            log.error( "processDicomDirWithDelay :: Error initiating DICOMDIR processing", e );
        }
    }


    private void checkAttributeNull ( DicomRequestDBObject dicomRequestDBObject, String attributeString, String tagName ) {
        log.info( "{}: {}", tagName, attributeString );
        if ( null != attributeString ) {
            switch ( tagName ) {
                case "Barcode" -> dicomRequestDBObject.setBarcode( attributeString );
                case "SOPInstanceUID" -> dicomRequestDBObject.setSopInstanceUid( attributeString );
                case "SeriesInstanceUID" -> dicomRequestDBObject.setSeriesInstanceUid( attributeString );
                case "StudyInstanceUID" -> dicomRequestDBObject.setOriginalStudyInstanceUid( attributeString );
                default -> throw new DicomAttributesException( "INVALID ATTRIBUTE", tagName + " is NULL" );
            }
        } else {
            if ( tagName.equalsIgnoreCase( WatchDirectoryConstant.BARCODE ) ) {
                eventNotificationService.sendEvent( kafkaTopicConfig.getEmail( ), "MISSING_BARCODE", null );
            }
            throw new DicomAttributesException( "INVALID ATTRIBUTE", tagName + " is NULL" );
        }
    }

    private String moveFileToTempStore ( String bucketName, File dicomFile, String sopInstanceID, String studyID, String seriesID, String deviceSerialNumber ) throws DicomAttributesException {
        log.info( "moveFileToTempStore :: =====================Creating file server path for current file=============================================>" );
        String objectName;
        if ( deviceSerialNumber != null ) {
            objectName = String.format( "%s/%s/%s/%s.dcm", deviceSerialNumber, studyID, seriesID, sopInstanceID );
        } else {
            objectName = String.format( "%s/%s/%s.dcm", studyID, seriesID, sopInstanceID );
        }

        Storage storage = StorageOptions.newBuilder( ).setRetrySettings( ServiceOptions.getDefaultRetrySettings( ) ).build( ).getService( );
        BlobInfo blobInfo = BlobInfo.newBuilder( bucketName, objectName ).setContentType( "application/dicom" ).build( );

        try ( WriteChannel writer = storage.writer( blobInfo ); FileInputStream input = new FileInputStream( dicomFile ) ) {
            writer.setChunkSize( 64 * 1024 * 1024 );
            byte[] buffer = new byte[ 64 * 1024 * 1024 ]; // 10 MB buffer
            int limit;
            while ( ( limit = input.read( buffer ) ) >= 0 ) {
                writer.write( ByteBuffer.wrap( buffer, 0, limit ) );
            }
            log.info( "moveFileToTempStore :: File uploaded successfully." );
        } catch ( IOException e ) {
            throw new DicomAttributesException( "UPLOAD_EXCEPTION", e.getMessage( ) );
        }
        return String.format( "gs://%s/%s", bucketName, objectName );
    }


    private String generateShortBarcode ( String studyUID, String seriesUID ) {
        try {
            String input = studyUID + seriesUID;
            MessageDigest md = MessageDigest.getInstance( "SHA-256" );
            byte[] hash = md.digest( input.getBytes( StandardCharsets.UTF_8 ) );

            // Convert to positive BigInteger and then to Base36
            String base36 = new BigInteger( 1, hash ).toString( 36 ).toUpperCase( );
            // Take the first 4 characters and prefix with "BC"
            return "BC-" + base36.substring( 0, 4 );
        } catch ( NoSuchAlgorithmException e ) {
            throw new DicomAttributesException( "SHA-256 algorithm not found", e.getMessage( ) );
        }
    }

}