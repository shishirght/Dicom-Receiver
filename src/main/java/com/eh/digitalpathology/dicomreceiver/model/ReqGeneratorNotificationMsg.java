package com.eh.digitalpathology.dicomreceiver.model;

public record ReqGeneratorNotificationMsg(String barcode, String sopInstanceUID, String seriesInstanceUID, String deviceSerialNumber) {}
