package com.eh.digitalpathology.dicomreceiver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "gcp-config")
public class GcpConfig {
    private String creds;
    private String dicomWebUrl;
    private String pathqaStoreUrl;
    private String researchStoreUrl;

    public String getCreds ( ) {
        return creds;
    }

    public void setCreds ( String creds ) {
        this.creds = creds;
    }

    public String getDicomWebUrl ( ) {
        return dicomWebUrl;
    }

    public void setDicomWebUrl ( String dicomWebUrl ) {
        this.dicomWebUrl = dicomWebUrl;
    }

    public String getPathqaStoreUrl ( ) {
        return pathqaStoreUrl;
    }

    public void setPathqaStoreUrl ( String pathqaStoreUrl ) {
        this.pathqaStoreUrl = pathqaStoreUrl;
    }

    public String getResearchStoreUrl ( ) {
        return researchStoreUrl;
    }

    public void setResearchStoreUrl ( String researchStoreUrl ) {
        this.researchStoreUrl = researchStoreUrl;
    }
}