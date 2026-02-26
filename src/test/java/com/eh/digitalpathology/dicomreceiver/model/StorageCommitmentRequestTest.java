package com.eh.digitalpathology.dicomreceiver.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageCommitmentRequestTest {

    @Test
    @DisplayName("Record components should be exposed via accessors")
    void testComponents() {
        StorageCommitmentTracker t1 = new StorageCommitmentTracker(
                "SOP-A", "SER-1", true, false, new Date(10_000), "R-1"
        );
        StorageCommitmentTracker t2 = new StorageCommitmentTracker(
                "SOP-B", "SER-1", true, true, new Date(20_000), "R-2"
        );

        StorageCommitmentRequest req = new StorageCommitmentRequest(
                "SER-1",
                true,
                List.of(t1, t2)
        );

        assertEquals("SER-1", req.seriesInstanceUid());
        assertTrue(req.storageCmtStatus());
        assertEquals(List.of(t1, t2), req.trackers());
        assertEquals(2, req.trackers().size());
    }

    @Test
    @DisplayName("Equality and hashCode across equal requests")
    void testEqualityAndHashCode() {
        StorageCommitmentTracker t = new StorageCommitmentTracker(
                "SOP", "SER", true, true, new Date(1_000), "RID"
        );
        List<StorageCommitmentTracker> list1 = List.of(t);
        List<StorageCommitmentTracker> list2 = List.of(
                new StorageCommitmentTracker("SOP", "SER", true, true, new Date(1_000), "RID")
        );

        StorageCommitmentRequest a = new StorageCommitmentRequest("SER", false, list1);
        StorageCommitmentRequest b = new StorageCommitmentRequest("SER", false, list2);
        StorageCommitmentRequest c = new StorageCommitmentRequest("SER-2", false, list1);

        // Equal by value (list contents equal, not necessarily same object)
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        // Different series -> not equal
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("toString should include core components")
    void testToString() {
        StorageCommitmentRequest req = new StorageCommitmentRequest(
                "SER-X", false, List.of()
        );
        String s = req.toString();
        assertTrue(s.contains("seriesInstanceUid=SER-X"));
        assertTrue(s.contains("storageCmtStatus=false"));
        assertTrue(s.contains("trackers=[]"));
    }

    @Test
    @DisplayName("Null handling: trackers or seriesInstanceUid may be null")
    void testNulls() {
        StorageCommitmentRequest req = new StorageCommitmentRequest(null, true, null);
        assertNull(req.seriesInstanceUid());
        assertTrue(req.storageCmtStatus());
        assertNull(req.trackers());
    }
}