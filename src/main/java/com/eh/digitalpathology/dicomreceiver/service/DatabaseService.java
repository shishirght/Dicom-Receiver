package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.DBRestClient;
import com.eh.digitalpathology.dicomreceiver.exceptions.DbConnectorExeption;
import com.eh.digitalpathology.dicomreceiver.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RefreshScope
public class DatabaseService {
    private static final Logger log = LoggerFactory.getLogger( DatabaseService.class.getName( ) );

    private final DBRestClient dbRestClient;
    @Value( "${db.connector.insert.uri}" )
    String uriInsert;

    @Autowired
    public DatabaseService ( DBRestClient dbRestClient ) {
        this.dbRestClient = dbRestClient;
    }


    public String insertDicomData ( DicomRequestDBObject requestDBObject, String sourceServiceName ) throws DbConnectorExeption {
        log.info( "insertDicomData :: Started inserting Dicom-Request-Object: {} \nwaiting for db reply..... ..........", requestDBObject );
        HttpHeaders headers = getHttpHeaders( sourceServiceName );
        try {
            return dbRestClient.exchange( HttpMethod.POST, uriInsert, requestDBObject, new ParameterizedTypeReference< ApiResponse< String > >( ) {
            }, httpHeaders -> httpHeaders.putAll( headers ) ).map( ApiResponse::status ).block( );
        } catch ( Exception ex ) {
            throw new DbConnectorExeption( "DB error", ex.getMessage( ) );
        }

    }

    public String saveStorageCommitment ( StorageCommitmentRequest request ) {
        try {
            return dbRestClient.exchange( HttpMethod.POST, "dicom/storage-commitment", request, new ParameterizedTypeReference< ApiResponse< String > >( ) {
            }, null ).map( ApiResponse::status ).block( );
        } catch ( Exception ex ) {
            throw new DbConnectorExeption( "DB error", ex.getMessage( ) );
        }
    }

    public Optional< DicomRequestDBObject > getDicomInstanceBySopInstanceUid ( String sopInstanceUid ) {
        try {
            ApiResponse< DicomRequestDBObject > response = dbRestClient.exchange( HttpMethod.GET, "dicom/instance/" + sopInstanceUid, null, new ParameterizedTypeReference< ApiResponse< DicomRequestDBObject > >( ) {
            }, null ).block( );

            if ( response != null ) {
                return Optional.ofNullable( response.content( ) );
            } else {
                log.error( "Received null response for SOPInstanceUID: {}", sopInstanceUid );
                return Optional.empty( );
            }
        } catch ( Exception ex ) {
            log.error( "Error fetching DicomInstance for SOPInstanceUID: {}", sopInstanceUid, ex );
            return Optional.empty( );
        }
    }


    private static HttpHeaders getHttpHeaders ( String serviceName ) {
        HttpHeaders headers = new HttpHeaders( );
        headers.add( "X-Service-Name", serviceName );
        return headers;
    }

    public String saveMetaDataInfo ( DicomDirDocument dicomDirDocument ) {
        try {
            return dbRestClient.exchange( HttpMethod.POST, "dicom/dicomdir", dicomDirDocument, new ParameterizedTypeReference< ApiResponse< String > >( ) {
            }, null ).map( ApiResponse::status ).block( );
        } catch ( Exception ex ) {
            throw new DbConnectorExeption( "DB error", ex.getMessage( ) );
        }
    }

    public Boolean isBarcodeExists ( String barcode ) {
        try {
            return dbRestClient.exchange( HttpMethod.GET, "qaslide/barcode/" + barcode, null, new ParameterizedTypeReference< ApiResponse< Boolean >>( ) {
            }, null ).map( ApiResponse::content ).block( );
        } catch ( Exception ex ) {
            throw new DbConnectorExeption( "DB error", ex.getMessage( ) );
        }
    }

    public SlideScanner fetchScannerByDeviceSerialNumber ( String deviceSerialNumber ) {
        try {
            ApiResponse< SlideScanner > response = dbRestClient.exchange( HttpMethod.GET, "scanner/" + deviceSerialNumber, null, new ParameterizedTypeReference< ApiResponse< SlideScanner > >( ) {
            }, null ).block( );

            if ( response != null ) {
                return response.content( );
            } else {
                log.error( "Received null response for DeviceSerialNumber: {}", deviceSerialNumber );
                return null;
            }
        } catch ( Exception ex ) {
            log.error( "Error fetching SlideScanner for DeviceSerialNumber: {}", deviceSerialNumber, ex );
            return null;
        }
    }
}