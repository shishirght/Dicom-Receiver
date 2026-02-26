package com.eh.digitalpathology.dicomreceiver.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Component
@RefreshScope
public class CommonUtils {

    private static final Logger logger = LoggerFactory.getLogger( CommonUtils.class.getName( ) );

    @Value( "${storescp.storage.path}" )
    private String fileStorePath;
    @Value( "${file.server.path}" )
    private String intermediateFileServer;
    @Value( "${sharedfolder.enableRemoteDirectoryWatcher}" )
    private boolean enableRemoteDirectoryWatcher;

    @Value( "${storescp.aetitle}" )
    private String aeName;
    @Value( "${storescp.aetitle.port}" )
    private Integer port;

    public String getLocalStoragePath ( ) {
        try {
            Files.createDirectories( Paths.get( fileStorePath ) );
        } catch ( IOException e ) {
            logger.error( "getLocalStoragePath :: unable to create directory :: {}", e.getMessage( ) );
        }
        String receivedFiles = Paths.get( fileStorePath ).toAbsolutePath( ).toString( );
        logger.info( "getLocalStoragePath :: Received Files Path: {}", receivedFiles );
        return receivedFiles;
    }


    public String getIntermediateFileServer ( ) {
        return intermediateFileServer;
    }

    public boolean isEnableRemoteDirectoryWatcher ( ) {
        return enableRemoteDirectoryWatcher;
    }

    public String getAeName ( ) {
        return aeName;
    }

    public Integer getPort ( ) {
        return port;
    }
}
