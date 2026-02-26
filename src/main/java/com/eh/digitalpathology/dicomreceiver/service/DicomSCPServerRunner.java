package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.service.storescp.DicomSCPServer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Component
public class DicomSCPServerRunner {

    private static final Logger log = LoggerFactory.getLogger( DicomSCPServerRunner.class );

    private final DicomSCPServer dicomSCPServer;

    public DicomSCPServerRunner ( DicomSCPServer dicomSCPServer ) {
        this.dicomSCPServer = dicomSCPServer;
    }

    @PostConstruct
    public void startDicomServer ( ) {
        new Thread( ( ) -> {
            try {
                log.debug( "startDicomServer :: Starting DICOM SCP Server..." );
                dicomSCPServer.start( );
                log.debug( "startDicomServer :: DICOM SCP Server started successfully." );
            } catch ( IOException | GeneralSecurityException e ) {
                log.error( "Failed to start DICOM SCP Server", e );
            }
        }, "DicomSCPServer-Thread" ).start( );
    }
}
