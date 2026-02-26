package com.eh.digitalpathology.dicomreceiver.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class StorageCommitmentTrackerTest {

    @Test
    @DisplayName("Record components should be exposed via accessors")
    void testComponents() {
        Date ts = new Date(1_733_000_000_000L); // deterministic timestamp
        StorageCommitmentTracker t = new StorageCommitmentTracker(
                "SOP-1",
                "SER-1",
                Boolean.TRUE,
                Boolean.FALSE,
                ts,
                "REQ-123"
        );

        assertEquals("SOP-1", t.sopInstanceUid());
        assertEquals("SER-1", t.seriesInstanceUid());
        assertEquals(Boolean.TRUE, t.cmtRequestStatus());
        assertEquals(Boolean.FALSE, t.cmtResponseStatus());
        assertEquals(ts, t.timestamp());
        assertEquals("REQ-123", t.requestId());
    }

    @Test
    @DisplayName("Equality and hashCode for identical records")
    void testEqualityAndHashCode() {
        Date ts = new Date(1_700_000_000_000L);

        StorageCommitmentTracker a = new StorageCommitmentTracker(
                "SOP", "SER", true, null, ts, "RID"
        );
        StorageCommitmentTracker b = new StorageCommitmentTracker(
                "SOP", "SER", true, null, ts, "RID"
        );
        StorageCommitmentTracker c = new StorageCommitmentTracker(
                "SOP-2", "SER", true, null, ts, "RID"
        );

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("toString should include field names/values")
    void testToString() {
        StorageCommitmentTracker t = new StorageCommitmentTracker(
                "SOP-X", "SER-Y", null, true, new Date(1000), "RID-9"
        );
        String s = t.toString();
        assertTrue(s.contains("sopInstanceUid=SOP-X"));
        assertTrue(s.contains("seriesInstanceUid=SER-Y"));
        assertTrue(s.contains("cmtRequestStatus=null"));
        assertTrue(s.contains("cmtResponseStatus=true"));
        assertTrue(s.contains("requestId=RID-9"));
    }

    @Test
    @DisplayName("Null handling: nullable components may be null")
    void testNulls() {
        StorageCommitmentTracker t = new StorageCommitmentTracker(
                null, null, null, null, null, null
        );
        assertNull(t.sopInstanceUid());
        assertNull(t.seriesInstanceUid());
        assertNull(t.cmtRequestStatus());
        assertNull(t.cmtResponseStatus());
        assertNull(t.timestamp());
        assertNull(t.requestId());
    }
}