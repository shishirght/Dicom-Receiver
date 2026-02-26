package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.KafkaTopicConfig;
import com.eh.digitalpathology.dicomreceiver.exceptions.DbConnectorExeption;
import com.eh.digitalpathology.dicomreceiver.model.DicomDirDocument;
import com.eh.digitalpathology.dicomreceiver.model.SgmtStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DicomDirService {

    private static final Logger log = LoggerFactory.getLogger( DicomDirService.class.getName( ) );
    private final DatabaseService databaseService;
    private final EventNotificationService eventNotificationService;
    private final ObjectMapper objectMapper = new ObjectMapper( );
    private final KafkaTopicConfig kafkaTopicConfig;


    @Autowired
    public DicomDirService ( DatabaseService databaseService, EventNotificationService eventNotificationService, KafkaTopicConfig kafkaTopicConfig ) {
        this.databaseService = databaseService;
        this.eventNotificationService = eventNotificationService;
        this.kafkaTopicConfig = kafkaTopicConfig;
    }

    public void fetchAndStoreMetaData ( Path filePath, DicomDirDocument dicomDirDocument ) throws IOException {
        log.info( "fetchAndStoreMetaData :: filePath of dicom dir file :: {}", filePath );
        try {
            if ( dicomDirDocument != null ) {
                log.info( "fetchAndStoreMetaData :: dicomDirDocument :: {}", dicomDirDocument.studyId( ) );
                String status = databaseService.saveMetaDataInfo( dicomDirDocument );
                log.info( "fetchAndStoreMetaData :: status :: {}", status );
                // on success delete file at /opt/received
                if ( status.equalsIgnoreCase( "success" ) ) {
                    SgmtStatus sgmtStatus = new SgmtStatus( "", dicomDirDocument.seriesId( ), "DICOM_DIR_CREATED" );
                    eventNotificationService.sendEvent( kafkaTopicConfig.getStgcmt( ), "", objectMapper.writeValueAsString( sgmtStatus ) );
                }
            }
        } catch ( IOException | DbConnectorExeption e ) {
            log.error( "fetchAndStoreMetaData :: Unable to convert DICOMDIR file to bytes {}", e.getMessage( ) );
        } finally {
            try {
                Files.deleteIfExists( filePath );
                log.info( "fetchAndStoreMetaData :: Deleted DICOMDIR file after processing: {}", filePath.getFileName( ) );
            } catch ( IOException e ) {
                log.error( "fetchAndStoreMetaData :: Failed to delete DICOMDIR file: {}", e.getMessage( ) );
            }
        }

    }

    public DicomDirDocument fetchMetaData ( Path filePath, Attributes dataSet ) {
        try {
            Sequence dirRecords = dataSet.getSequence( Tag.DirectoryRecordSequence );
            if ( dirRecords == null ) {
                return null;
            }
            String studyId = null;
            String seriesId = null;
            int imageCount = 0;

            for ( Attributes item : dirRecords ) {
                String type = item.getString( Tag.DirectoryRecordType );
                if ( "STUDY".equalsIgnoreCase( type ) && studyId == null ) {
                    studyId = item.getString( Tag.StudyInstanceUID );

                } else if ( "SERIES".equalsIgnoreCase( type ) && seriesId == null ) {
                    seriesId = item.getString( Tag.SeriesInstanceUID );

                } else if ( "IMAGE".equalsIgnoreCase( type ) ) {
                    imageCount++;
                }
            }
            byte[] dicomDirBinary = Files.readAllBytes( filePath );
            return new DicomDirDocument( studyId, seriesId, imageCount, dicomDirBinary );

        } catch ( IOException e ) {
            log.error( "fetchMetaData :: Unable to extract DICOMDIR file:: {}", e.getMessage( ) );
        }
        return null;
    }

}
