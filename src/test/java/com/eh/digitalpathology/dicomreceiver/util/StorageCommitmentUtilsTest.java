package com.eh.digitalpathology.dicomreceiver.util;

import com.eh.digitalpathology.dicomreceiver.model.StorageCommitmentRequest;
import com.eh.digitalpathology.dicomreceiver.model.StorageCommitmentTracker;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StorageCommitmentUtilsTest {

    @Test
    void testCreateSuccessItem() {
        String sopClassUID = "1.2.3.4";
        String sopInstanceUID = "5.6.7.8";
        Attributes item = StorageCommitmentUtils.createSuccessItem(sopClassUID, sopInstanceUID);

        assertNotNull(item);
        assertEquals(sopClassUID, item.getString(Tag.ReferencedSOPClassUID));
        assertEquals(sopInstanceUID, item.getString(Tag.ReferencedSOPInstanceUID));
    }

    @Test
    void testCreateFailedItem() {
        String sopClassUID = "1.2.3.4";
        String sopInstanceUID = "5.6.7.8";

        Attributes item = StorageCommitmentUtils.createFailedItem(sopClassUID, sopInstanceUID);

        assertNotNull(item);
        assertEquals(sopClassUID, item.getString(Tag.ReferencedSOPClassUID));
        assertEquals(sopInstanceUID, item.getString(Tag.ReferencedSOPInstanceUID));
        assertEquals(0x0110, item.getInt(Tag.FailureReason, 0));
    }

    @Test
    void testBuildTracker() {
        String seriesId = "SERIES001";
        String sopInstanceUID = "SOP123";
        String requestId = "requestId001";

        StorageCommitmentTracker tracker = StorageCommitmentUtils.buildTracker(seriesId, sopInstanceUID, true, requestId);

        assertNotNull(tracker);
        assertEquals(seriesId, tracker.seriesInstanceUid());
        assertEquals(sopInstanceUID, tracker.sopInstanceUid());
        assertEquals(requestId, tracker.requestId());
        assertTrue(tracker.cmtRequestStatus());
        assertNotNull(tracker.timestamp());
    }

    @Test
    void testBuildCommitRequest() {
        StorageCommitmentTracker tracker =
                new StorageCommitmentTracker("SOP123", "SERIES001", true, true, new Date(), "requestId001");

        List<StorageCommitmentTracker> trackers = List.of(tracker);

        StorageCommitmentRequest request =
                StorageCommitmentUtils.buildCommitRequest("SERIES001", trackers, true);

        assertNotNull(request);
        assertEquals("SERIES001", request.seriesInstanceUid());
        assertTrue(request.storageCmtStatus());
        assertEquals(1, request.trackers().size());

    }


}