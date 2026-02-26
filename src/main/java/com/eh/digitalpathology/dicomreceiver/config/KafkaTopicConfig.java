package com.eh.digitalpathology.dicomreceiver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "kafka.topic")
@RefreshScope
public class KafkaTopicConfig {
    private String receiver;
    private String stgcmt;
    private String email;
    private String pathqa;
    private String scanProgress;

    public String getPathqa ( ) {
        return pathqa;
    }

    public void setPathqa ( String pathqa ) {
        this.pathqa = pathqa;
    }

    public String getEmail ( ) {
        return email;
    }

    public void setEmail ( String email ) {
        this.email = email;
    }

    public String getStgcmt ( ) {
        return stgcmt;
    }

    public void setStgcmt ( String stgcmt ) {
        this.stgcmt = stgcmt;
    }

    public String getReceiver ( ) {
        return receiver;
    }

    public void setReceiver ( String receiver ) {
        this.receiver = receiver;
    }

    public String getScanProgress() {
        return scanProgress;
    }

    public void setScanProgress(String scanProgress) {
        this.scanProgress = scanProgress;
    }
}
