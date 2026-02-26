package com.eh.digitalpathology.dicomreceiver.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SharedFolderConfigTest {

    @Test
    @DisplayName("Should set and get all SharedFolder configuration properties")
    void testSharedFolderConfigProperties() {
        SharedFolderConfig config = new SharedFolderConfig();

        config.setServername("SERVER-01");
        config.setSharepath("\\\\SERVER-01\\dicom-share");
        config.setUsername("admin");
        config.setPassword("secret-password");

        assertEquals("SERVER-01", config.getServername());
        assertEquals("\\\\SERVER-01\\dicom-share", config.getSharepath());
        assertEquals("admin", config.getUsername());
        assertEquals("secret-password", config.getPassword());
    }

    @Test
    @DisplayName("Default values should be null before any assignment")
    void testDefaultNullValues() {
        SharedFolderConfig config = new SharedFolderConfig();

        assertNull(config.getServername());
        assertNull(config.getSharepath());
        assertNull(config.getUsername());
        assertNull(config.getPassword());
    }
}