package com.eh.digitalpathology.dicomreceiver.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SgmtStatusTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Record components should be exposed via accessors")
    void testComponents() {
        SgmtStatus st = new SgmtStatus("REQ-1", "SER-123", "COMPLETED");

        assertEquals("REQ-1", st.cmtReqId());
        assertEquals("SER-123", st.seriesInstanceUid());
        assertEquals("COMPLETED", st.status());
    }

    @Test
    @DisplayName("Equality and hashCode across equal records")
    void testEqualityAndHashCode() {
        SgmtStatus a = new SgmtStatus("R", "S", "OK");
        SgmtStatus b = new SgmtStatus("R", "S", "OK");
        SgmtStatus c = new SgmtStatus("R2", "S", "OK");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("toString should include all components")
    void testToString() {
        SgmtStatus st = new SgmtStatus("R", "S", "OK");
        String ts = st.toString();
        assertTrue(ts.contains("cmtReqId=R"));
        assertTrue(ts.contains("seriesInstanceUid=S"));
        assertTrue(ts.contains("status=OK"));
    }

    @Test
    @DisplayName("@JsonIgnoreProperties(ignoreUnknown = true): unknown JSON fields are ignored")
    void testJsonIgnoreUnknown() throws Exception {
        String json = """
            {
              "cmtReqId": "REQ-77",
              "seriesInstanceUid": "SER-77",
              "status": "IN_PROGRESS",
              "extra": "ignored-field",
              "anotherOne": 123
            }
            """;

        SgmtStatus parsed = mapper.readValue(json, SgmtStatus.class);
        assertEquals("REQ-77", parsed.cmtReqId());
        assertEquals("SER-77", parsed.seriesInstanceUid());
        assertEquals("IN_PROGRESS", parsed.status());
        // If unknown fields were NOT ignored, this would have thrown an exception.
    }
}