package com.eh.digitalpathology.dicomreceiver.model;

public record PathQa(String barcodeValue, String studyInstanceUid, String seriesInstanceUid, String deviceSerialNumber) {
}
