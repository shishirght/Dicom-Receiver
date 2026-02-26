package com.eh.digitalpathology.dicomreceiver.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReqGeneratorNotificationMsgTest {

    @Test
    @DisplayName("Record components should be exposed via accessors")
    void testComponents() {
        ReqGeneratorNotificationMsg msg =
                new ReqGeneratorNotificationMsg("BC-1", "SOP-123", "SER-456", "DEV-999");

        assertEquals("BC-1", msg.barcode());
        assertEquals("SOP-123", msg.sopInstanceUID());
        assertEquals("SER-456", msg.seriesInstanceUID());
        assertEquals("DEV-999", msg.deviceSerialNumber());
    }

    @Test
    @DisplayName("Equality and hashCode should consider all components")
    void testEqualityAndHashCode() {
        ReqGeneratorNotificationMsg a =
                new ReqGeneratorNotificationMsg("B", "SOP", "SER", "DEV");
        ReqGeneratorNotificationMsg b =
                new ReqGeneratorNotificationMsg("B", "SOP", "SER", "DEV");
        ReqGeneratorNotificationMsg c =
                new ReqGeneratorNotificationMsg("B2", "SOP", "SER", "DEV");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("toString should include field names and values")
    void testToString() {
        ReqGeneratorNotificationMsg msg =
                new ReqGeneratorNotificationMsg("B", "SOP", "SER", "DEV");
        String ts = msg.toString();

        assertTrue(ts.contains("barcode=B"));
        assertTrue(ts.contains("sopInstanceUID=SOP"));
        assertTrue(ts.contains("seriesInstanceUID=SER"));
        assertTrue(ts.contains("deviceSerialNumber=DEV"));
    }
}
