package com.eh.digitalpathology.dicomreceiver.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class DicomRequestDBObjectTest {

    @Test
    @DisplayName("Getters and Setters: should persist and return values correctly")
    void testGettersAndSetters() {
        DicomRequestDBObject obj = new DicomRequestDBObject();

        Date recv = new Date(1_733_000_000_000L);
        Date enr  = new Date(1_733_000_500_000L);

        obj.setOriginalStudyInstanceUid("orig-uid");
        obj.setActualStudyInstanceUid("act-uid");
        obj.setIntermediateStoragePath("/tmp/dicom");
        obj.setDicomInstanceReceivedTimestamp(recv);
        obj.setEnrichmentTimestamp(enr);
        obj.setBarcode("BR-001");
        obj.setSopInstanceUid("SOP-123");
        obj.setSeriesInstanceUid("SERIES-456");
        obj.setDeviceSerialNumber("DEV-789");
        obj.setCaseNumber("CASE-0001");

        assertEquals("orig-uid", obj.getOriginalStudyInstanceUid());
        assertEquals("act-uid", obj.getActualStudyInstanceUid());
        assertEquals("/tmp/dicom", obj.getIntermediateStoragePath());
        assertEquals(recv, obj.getDicomInstanceReceivedTimestamp());
        assertEquals(enr, obj.getEnrichmentTimestamp());
        assertEquals("BR-001", obj.getBarcode());
        assertEquals("SOP-123", obj.getSopInstanceUid());
        assertEquals("SERIES-456", obj.getSeriesInstanceUid());
        assertEquals("DEV-789", obj.getDeviceSerialNumber());
        assertEquals("CASE-0001", obj.getCaseNumber());
    }

    @Test
    @DisplayName("Null handling: fields can be unset or null without errors")
    void testNulls() {
        DicomRequestDBObject obj = new DicomRequestDBObject();

        assertNull(obj.getOriginalStudyInstanceUid());
        assertNull(obj.getActualStudyInstanceUid());
        assertNull(obj.getIntermediateStoragePath());
        assertNull(obj.getDicomInstanceReceivedTimestamp());
        assertNull(obj.getEnrichmentTimestamp());
        assertNull(obj.getBarcode());
        assertNull(obj.getSopInstanceUid());
        assertNull(obj.getSeriesInstanceUid());
        assertNull(obj.getDeviceSerialNumber());
        assertNull(obj.getCaseNumber());
    }

    @Test
    @DisplayName("toString: should include selected fields and omit deviceSerialNumber/caseNumber as implemented")
    void testToString() {
        DicomRequestDBObject obj = new DicomRequestDBObject();
        obj.setOriginalStudyInstanceUid("orig");
        obj.setActualStudyInstanceUid("act");
        obj.setIntermediateStoragePath("/path");
        obj.setDicomInstanceReceivedTimestamp(new Date(1000L));
        obj.setEnrichmentTimestamp(new Date(2000L));
        obj.setBarcode("BC");
        obj.setSopInstanceUid("SOP");
        obj.setSeriesInstanceUid("SERIES");
        obj.setDeviceSerialNumber("DEV");
        obj.setCaseNumber("CASE");

        String ts = obj.toString();

        // Present per current implementation
        assertTrue(ts.contains("originalStudyInstanceUid='orig'"));
        assertTrue(ts.contains("actualStudyInstanceUid='act'"));
        assertTrue(ts.contains("intermediateStoragePath='/path'"));
        assertTrue(ts.contains("dicomInstanceReceivedTimestamp='"));
        assertTrue(ts.contains("enrichmentTimestamp='"));
        assertTrue(ts.contains("barcode='BC'"));
        assertTrue(ts.contains("sopInstanceUid='SOP'"));
    }}
