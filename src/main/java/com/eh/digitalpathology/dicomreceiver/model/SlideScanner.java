package com.eh.digitalpathology.dicomreceiver.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SlideScanner(String id, String name, String model, String location, String department, String dicomStore, String aeTitle, String deviceId, String deviceSerialNumber, boolean research, boolean connected) {
}