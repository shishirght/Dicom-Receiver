package com.eh.digitalpathology.dicomreceiver.service.storescp;

import com.eh.digitalpathology.dicomreceiver.util.CommonUtils;
import jakarta.annotation.PostConstruct;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executors;

@Service
public class DicomSCPServer {
    private final Device device;

    private final ApplicationEntity ae;
    private final Connection conn;
    // Storage Commitment SOP Class UID (Push Model)
    public static final String STORAGE_COMMITMENT_PUSH_MODEL_SOP_CLASS = "1.2.840.10008.1.20.1";

    private final StorageCommitmentSCPService storageCommitmentSCPService;
    private final DicomStoreSCPService dicomStoreSCPService;
    private final CommonUtils commonUtils;

    public DicomSCPServer ( StorageCommitmentSCPService storageCommitmentSCPService, DicomStoreSCPService dicomStoreSCPService, CommonUtils commonUtils ) {
        this.storageCommitmentSCPService = storageCommitmentSCPService;
        this.dicomStoreSCPService = dicomStoreSCPService;
        this.commonUtils = commonUtils;
        device = new Device( "dicom-scp" );
        ae = new ApplicationEntity( );
        conn = new Connection( );


        device.setExecutor( Executors.newCachedThreadPool( ) );
        device.setScheduledExecutor( Executors.newSingleThreadScheduledExecutor( ) );

        device.addConnection( conn );
        device.addApplicationEntity( ae );
        ae.addConnection( conn );

        DicomServiceRegistry serviceRegistry = new DicomServiceRegistry( );
        serviceRegistry.addDicomService( this.dicomStoreSCPService );
        serviceRegistry.addDicomService( this.storageCommitmentSCPService );
        device.setDimseRQHandler( serviceRegistry );

        // Define SOP class support

        ae.addTransferCapability( new TransferCapability( null, UID.VLWholeSlideMicroscopyImageStorage, TransferCapability.Role.SCP, UID.ExplicitVRLittleEndian, UID.ImplicitVRLittleEndian, UID.JPEGBaseline8Bit ) );

        ae.addTransferCapability( new TransferCapability( null, UID.StorageCommitmentPushModel, TransferCapability.Role.SCP, UID.ImplicitVRLittleEndian, UID.ExplicitVRLittleEndian ) );

        ae.addTransferCapability( new TransferCapability( null, STORAGE_COMMITMENT_PUSH_MODEL_SOP_CLASS, TransferCapability.Role.SCP, UID.ImplicitVRLittleEndian ) );
    }


    @PostConstruct
    public void init ( ) {
        ae.setAETitle( commonUtils.getAeName( ) );
        conn.setPort( commonUtils.getPort( ) );
    }

    public void start ( ) throws IOException, GeneralSecurityException {
        device.bindConnections( );
    }
}
