package com.eh.digitalpathology.dicomreceiver.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SlideScanProgressEvent(String slideBarcode, String deviceSerialNumber, String scanStatus,
                                     String sourceService) {

    private static final String DEFAULT_SOURCE = "eh-dp-dicom-receiver";

    public SlideScanProgressEvent(String slideBarcode, String deviceSerialNumber, String scanStatus) {
        this(slideBarcode, deviceSerialNumber, scanStatus, DEFAULT_SOURCE);
    }
}
