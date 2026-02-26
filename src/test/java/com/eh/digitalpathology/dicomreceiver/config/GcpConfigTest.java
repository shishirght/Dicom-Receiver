package com.eh.digitalpathology.dicomreceiver.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GcpConfigTest {

    @Test
    @DisplayName("Should set and get all GCP config properties")
    void testGcpConfigProperties() {
        GcpConfig config = new GcpConfig();

        config.setCreds("creds-json-content");
        config.setDicomWebUrl("https://gcp.dicom/web");
        config.setPathqaStoreUrl("https://gcp.pathqa/store");
        config.setResearchStoreUrl("https://gcp.research/store");

        assertEquals("creds-json-content", config.getCreds());
        assertEquals("https://gcp.dicom/web", config.getDicomWebUrl());
        assertEquals("https://gcp.pathqa/store", config.getPathqaStoreUrl());
        assertEquals("https://gcp.research/store", config.getResearchStoreUrl());
    }

    @Test
    @DisplayName("GcpConfig should allow null values by default")
    void testDefaultNullValues() {
        GcpConfig config = new GcpConfig();

        assertNull(config.getCreds());
        assertNull(config.getDicomWebUrl());
        assertNull(config.getPathqaStoreUrl());
        assertNull(config.getResearchStoreUrl());
    }
}