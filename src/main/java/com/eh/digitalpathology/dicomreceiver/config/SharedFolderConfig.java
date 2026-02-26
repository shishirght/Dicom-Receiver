package com.eh.digitalpathology.dicomreceiver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "sharedfolder")
@RefreshScope
public class SharedFolderConfig {

    private String servername;

    private String sharepath;
    private String username;
    private String password;

    public String getServername ( ) {
        return servername;
    }

    public void setServername ( String servername ) {
        this.servername = servername;
    }

    public String getSharepath ( ) {
        return sharepath;
    }

    public void setSharepath ( String sharepath ) {
        this.sharepath = sharepath;
    }

    public String getUsername ( ) {
        return username;
    }

    public void setUsername ( String username ) {
        this.username = username;
    }

    public String getPassword ( ) {
        return password;
    }

    public void setPassword ( String password ) {
        this.password = password;
    }
}
