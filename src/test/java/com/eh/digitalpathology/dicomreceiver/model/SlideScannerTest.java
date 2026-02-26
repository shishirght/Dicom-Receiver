package com.eh.digitalpathology.dicomreceiver.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlideScannerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Record components should be exposed via accessors (including booleans)")
    void testComponents() {
        SlideScanner sc = new SlideScanner(
                "id-1", "Scanner A", "Aperio-X", "Pune", "Pathology",
                "dicom-store-1", "AET-SC01", "D-123", "SER-999",
                true, false
        );

        assertEquals("id-1", sc.id());
        assertEquals("Scanner A", sc.name());
        assertEquals("Aperio-X", sc.model());
        assertEquals("Pune", sc.location());
        assertEquals("Pathology", sc.department());
        assertEquals("dicom-store-1", sc.dicomStore());
        assertEquals("AET-SC01", sc.aeTitle());
        assertEquals("D-123", sc.deviceId());
        assertEquals("SER-999", sc.deviceSerialNumber());
        assertTrue(sc.research());
        assertFalse(sc.connected());
    }

    @Test
    @DisplayName("Equality and hashCode should compare all fields")
    void testEqualityAndHashCode() {
        SlideScanner a = new SlideScanner(
                "id", "n", "m", "loc", "dept",
                "store", "AE", "devId", "serial", true, true
        );
        SlideScanner b = new SlideScanner(
                "id", "n", "m", "loc", "dept",
                "store", "AE", "devId", "serial", true, true
        );
        SlideScanner c = new SlideScanner(
                "id2", "n", "m", "loc", "dept",
                "store", "AE", "devId", "serial", true, true
        );

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("toString should include field names and values")
    void testToString() {
        SlideScanner sc = new SlideScanner(
                "i", "n", "m", "l", "d",
                "s", "AE", "dev", "ser", false, true
        );
        String ts = sc.toString();
        assertTrue(ts.contains("id=i"));
        assertTrue(ts.contains("name=n"));
        assertTrue(ts.contains("model=m"));
        assertTrue(ts.contains("location=l"));
        assertTrue(ts.contains("department=d"));
        assertTrue(ts.contains("dicomStore=s"));
        assertTrue(ts.contains("aeTitle=AE"));
        assertTrue(ts.contains("deviceId=dev"));
        assertTrue(ts.contains("deviceSerialNumber=ser"));
        assertTrue(ts.contains("research=false"));
        assertTrue(ts.contains("connected=true"));
    }

    @Test
    @DisplayName("@JsonIgnoreProperties(ignoreUnknown = true): unknown JSON fields are ignored")
    void testJsonIgnoreUnknown() throws Exception {
        String json = """
            {
              "id": "sc-77",
              "name": "Scanner 77",
              "model": "Leica-XYZ",
              "location": "Lab-3",
              "department": "Histology",
              "dicomStore": "store-77",
              "aeTitle": "AE77",
              "deviceId": "DEV-77",
              "deviceSerialNumber": "SER-77",
              "research": true,
              "connected": false,
              "unexpected": "ignored",
              "another": 42
            }
            """;

        SlideScanner parsed = mapper.readValue(json, SlideScanner.class);
        assertEquals("sc-77", parsed.id());
        assertEquals("Scanner 77", parsed.name());
        assertEquals("Leica-XYZ", parsed.model());
        assertEquals("Lab-3", parsed.location());
        assertEquals("Histology", parsed.department());
        assertEquals("store-77", parsed.dicomStore());
        assertEquals("AE77", parsed.aeTitle());
        assertEquals("DEV-77", parsed.deviceId());
        assertEquals("SER-77", parsed.deviceSerialNumber());
        assertTrue(parsed.research());
        assertFalse(parsed.connected());
        // No exception thrown despite unknown JSON fields.
    }
}