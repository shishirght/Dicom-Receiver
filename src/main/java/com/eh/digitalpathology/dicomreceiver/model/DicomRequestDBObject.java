package com.eh.digitalpathology.dicomreceiver.model;

import java.util.Date;

public class DicomRequestDBObject {
    private String originalStudyInstanceUid;
    private String actualStudyInstanceUid;
    private String intermediateStoragePath;
    private Date dicomInstanceReceivedTimestamp;
    private Date enrichmentTimestamp;
    private String barcode;
    private String sopInstanceUid;
    private String seriesInstanceUid;
    private String deviceSerialNumber;
    private String caseNumber;

    // Getters and Setters
    public String getOriginalStudyInstanceUid() {
        return originalStudyInstanceUid;
    }

    public void setOriginalStudyInstanceUid(String originalStudyInstanceUid) {
        this.originalStudyInstanceUid = originalStudyInstanceUid;
    }

    public String getActualStudyInstanceUid() {
        return actualStudyInstanceUid;
    }

    public void setActualStudyInstanceUid(String actualStudyInstanceUid) {
        this.actualStudyInstanceUid = actualStudyInstanceUid;
    }

    public String getIntermediateStoragePath() {
        return intermediateStoragePath;
    }

    public void setIntermediateStoragePath(String intermediateStoragePath) {
        this.intermediateStoragePath = intermediateStoragePath;
    }

    public Date getDicomInstanceReceivedTimestamp() {
        return dicomInstanceReceivedTimestamp;
    }

    public void setDicomInstanceReceivedTimestamp(Date dicomInstanceReceivedTimestamp) {
        this.dicomInstanceReceivedTimestamp = dicomInstanceReceivedTimestamp;
    }

    public Date getEnrichmentTimestamp() {
        return enrichmentTimestamp;
    }

    public void setEnrichmentTimestamp(Date enrichmentTimestamp) {
        this.enrichmentTimestamp = enrichmentTimestamp;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getSopInstanceUid() {
        return sopInstanceUid;
    }

    public void setSopInstanceUid(String sopInstanceUid) {
        this.sopInstanceUid = sopInstanceUid;
    }

    public String getSeriesInstanceUid() {
        return seriesInstanceUid;
    }

    public void setSeriesInstanceUid(String seriesInstanceUid) {
        this.seriesInstanceUid = seriesInstanceUid;
    }

    public String getDeviceSerialNumber ( ) {
        return deviceSerialNumber;
    }

    public void setDeviceSerialNumber ( String deviceSerialNumber ) {
        this.deviceSerialNumber = deviceSerialNumber;
    }

    public String getCaseNumber ( ) {
        return caseNumber;
    }

    public void setCaseNumber ( String caseNumber ) {
        this.caseNumber = caseNumber;
    }

    @Override
    public String toString() {
        return "DicomRequestDBObject{" +
                "originalStudyInstanceUid='" + originalStudyInstanceUid + '\'' +
                ", actualStudyInstanceUid='" + actualStudyInstanceUid + '\'' +
                ", intermediateStoragePath='" + intermediateStoragePath + '\'' +
                ", dicomInstanceReceivedTimestamp='" + dicomInstanceReceivedTimestamp + '\'' +
                ", enrichmentTimestamp='" + enrichmentTimestamp + '\'' +
                ", barcode='" + barcode + '\'' +
                ", sopInstanceUid='" + sopInstanceUid + '\'' +
                ", seriesInstanceUid='" + seriesInstanceUid + '\'' +
                '}';
    }
}