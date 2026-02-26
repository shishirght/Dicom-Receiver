package com.eh.digitalpathology.dicomreceiver.service.storescp;

import com.eh.digitalpathology.dicomreceiver.config.KafkaTopicConfig;
import com.eh.digitalpathology.dicomreceiver.model.DicomRequestDBObject;
import com.eh.digitalpathology.dicomreceiver.model.SgmtStatus;
import com.eh.digitalpathology.dicomreceiver.model.StorageCommitmentRequest;
import com.eh.digitalpathology.dicomreceiver.model.StorageCommitmentTracker;
import com.eh.digitalpathology.dicomreceiver.service.DatabaseService;
import com.eh.digitalpathology.dicomreceiver.service.EventNotificationService;
import com.eh.digitalpathology.dicomreceiver.util.StorageCommitmentUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dcm4che3.data.*;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.net.*;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.DicomService;
import org.dcm4che3.net.service.DicomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Component
public class StorageCommitmentSCPService implements DicomService {

    private static final Logger log = LoggerFactory.getLogger( StorageCommitmentSCPService.class );

    public static final String STORAGE_COMMITMENT_PUSH_MODEL_SOP_CLASS = "1.2.840.10008.1.20.1";
    public static final String STORAGE_COMMITMENT_PUSH_MODEL_SOP_INSTANCE = "1.2.840.10008.1.20.1.1";

    private final DatabaseService databaseService;
    private final EventNotificationService eventNotificationService;
    private final KafkaTopicConfig kafkaTopicConfig;

    private final ObjectMapper objectMapper = new ObjectMapper( );


    public StorageCommitmentSCPService ( DatabaseService databaseService, EventNotificationService eventNotificationService, KafkaTopicConfig kafkaTopicConfig ) {
        this.databaseService = databaseService;
        this.eventNotificationService = eventNotificationService;
        this.kafkaTopicConfig = kafkaTopicConfig;
    }

    @Override
    public void onDimseRQ ( Association as, PresentationContext pc, Dimse dimse, Attributes cmd, PDVInputStream data ) throws IOException {
        if ( dimse != Dimse.N_ACTION_RQ ) {
            throw new DicomServiceException( Status.UnrecognizedOperation, "Only N-ACTION is supported for Storage Commitment" );
        }

        Attributes actionInfo = readActionInfo( data );
        String transactionUID = actionInfo.getString( Tag.TransactionUID );
        Sequence refSOPSeq = actionInfo.getSequence( Tag.ReferencedSOPSequence );

        validateActionInfo( transactionUID, refSOPSeq );

        log.info( "onDimseRQ :: Received N-ACTION for Transaction UID: {} with {} SOP Instances", transactionUID, refSOPSeq.size( ) );

        Attributes eventInfo = prepareEventInfo( transactionUID, refSOPSeq );
        List< StorageCommitmentTracker > trackers = processReferencedSOPs( refSOPSeq, eventInfo, transactionUID );

        int eventTypeID = eventInfo.getSequence( Tag.FailedSOPSequence ).isEmpty( ) ? 1 : 2;
        sendEventReport( as, eventInfo, eventTypeID );
        sendResponse( as, pc, cmd );
        persistStorageCommitment( transactionUID, trackers, eventTypeID );
    }

    private Attributes readActionInfo ( PDVInputStream data ) throws IOException {
        try ( DicomInputStream dis = new DicomInputStream( data ) ) {
            return dis.readDataset( -1 );
        }
    }

    private void validateActionInfo ( String transactionUID, Sequence refSOPSeq ) throws DicomServiceException {
        if ( transactionUID == null || refSOPSeq == null ) {
            throw new DicomServiceException( Status.MissingAttributeValue, "Missing Transaction UID or Referenced SOP Sequence" );
        }
    }

    private Attributes prepareEventInfo ( String transactionUID, Sequence refSOPSeq ) {
        Attributes eventInfo = new Attributes( );
        eventInfo.setString( Tag.TransactionUID, VR.UI, transactionUID );
        eventInfo.newSequence( Tag.ReferencedSOPSequence, refSOPSeq.size( ) );
        eventInfo.newSequence( Tag.FailedSOPSequence, refSOPSeq.size( ) );
        return eventInfo;
    }

    private List< StorageCommitmentTracker > processReferencedSOPs ( Sequence refSOPSeq, Attributes eventInfo, String transactionUID ) {
        List< StorageCommitmentTracker > trackers = new ArrayList<>( );
        Sequence successSeq = eventInfo.getSequence( Tag.ReferencedSOPSequence );
        Sequence failedSeq = eventInfo.getSequence( Tag.FailedSOPSequence );
        String seriesId = null;

        for ( Attributes item : refSOPSeq ) {
            String sopClassUID = item.getString( Tag.ReferencedSOPClassUID );
            String sopInstanceUID = item.getString( Tag.ReferencedSOPInstanceUID );

            if ( sopClassUID == null || sopInstanceUID == null ) {
                log.warn( "onDimseRQ :: Missing SOP Class UID or Instance UID in a referenced item" );
                continue;
            }

            Optional< DicomRequestDBObject > dicomRecord = databaseService.getDicomInstanceBySopInstanceUid( sopInstanceUID );
            boolean found = dicomRecord.isPresent( );
            if ( found ) {
                seriesId = seriesId == null ? dicomRecord.get( ).getSeriesInstanceUid( ) : seriesId;
                successSeq.add( StorageCommitmentUtils.createSuccessItem( sopClassUID, sopInstanceUID ) );
            } else {
                failedSeq.add( StorageCommitmentUtils.createFailedItem( sopClassUID, sopInstanceUID ) );
            }
            trackers.add( StorageCommitmentUtils.buildTracker( seriesId, sopInstanceUID, found, transactionUID ) );
        }
        return trackers;
    }

    private void sendEventReport ( Association as, Attributes eventInfo, int eventTypeID ) throws IOException {
        try {
            log.info( "Sending N-EVENT-REPORT (eventTypeID = {}): {} success, {} failed", eventTypeID, eventInfo.getSequence( Tag.ReferencedSOPSequence ).size( ), eventInfo.getSequence( Tag.FailedSOPSequence ).size( ) );

            as.neventReport( STORAGE_COMMITMENT_PUSH_MODEL_SOP_CLASS, STORAGE_COMMITMENT_PUSH_MODEL_SOP_INSTANCE, eventTypeID, eventInfo, null );
        } catch ( InterruptedException e ) {
            log.error( "Interrupted while sending N-EVENT-REPORT", e );
            Thread.currentThread( ).interrupt( );
        }
    }

    private void sendResponse ( Association as, PresentationContext pc, Attributes cmd ) throws IOException {
        Attributes rsp = Commands.mkNActionRSP( cmd, Status.Success );
        as.writeDimseRSP( pc, rsp, null );
    }

    private void persistStorageCommitment ( String transactionUID, List< StorageCommitmentTracker > trackers, int eventTypeID ) throws IOException {
        if ( trackers.isEmpty( ) ) return;

        String seriesId = trackers.get( 0 ).seriesInstanceUid( );
        boolean allSuccess = eventTypeID == 1;

        StorageCommitmentRequest commitRequest = StorageCommitmentUtils.buildCommitRequest( seriesId, trackers, allSuccess );
        String status = databaseService.saveStorageCommitment( commitRequest );

        log.info( "saveStorageCommitment response: {}", status );

        if ( "success".equalsIgnoreCase( status ) ) {
            log.info( "onDimseRQ :: Storage commitment persisted successfully." );
            SgmtStatus sgmtStatus = new SgmtStatus( transactionUID, seriesId, "STORAGE_COMMITMENT" );
            eventNotificationService.sendEvent( kafkaTopicConfig.getStgcmt( ), "", objectMapper.writeValueAsString( sgmtStatus ) );
        } else {
            log.warn( "onDimseRQ :: Storage commitment DB save failed; Kafka not notified." );
        }
    }

    @Override
    public void onClose ( Association association ) {
        log.info( " onClose :: Association closed with {}", association.getRemoteAET( ) );
    }

    @Override
    public String[] getSOPClasses ( ) {
        return new String[]{ UID.StorageCommitmentPushModel };
    }
}
