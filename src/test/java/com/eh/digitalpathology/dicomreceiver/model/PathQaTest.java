package com.eh.digitalpathology.dicomreceiver.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathQaTest {

    @Test
    @DisplayName("Should expose PathQa record components")
    void testRecordComponents() {
        PathQa qa = new PathQa("BARCODE123", "study-uid", "series-uid", "DEVICE-999");

        assertEquals("BARCODE123", qa.barcodeValue());
        assertEquals("study-uid", qa.studyInstanceUid());
        assertEquals("series-uid", qa.seriesInstanceUid());
        assertEquals("DEVICE-999", qa.deviceSerialNumber());
    }

    @Test
    @DisplayName("Equality and hashCode: identical records are equal")
    void testEqualityAndHashCode() {
        PathQa a = new PathQa("B", "S", "R", "D");
        PathQa b = new PathQa("B", "S", "R", "D");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new PathQa("B2", "S", "R", "D"));
    }

    @Test
    @DisplayName("toString: should include field names and values")
    void testToStringFormat() {
        PathQa qa = new PathQa("B", "S", "R", "D");
        String ts = qa.toString();

        assertTrue(ts.contains("barcodeValue=B"));
        assertTrue(ts.contains("studyInstanceUid=S"));
        assertTrue(ts.contains("seriesInstanceUid=R"));
        assertTrue(ts.contains("deviceSerialNumber=D"));
    }
}
