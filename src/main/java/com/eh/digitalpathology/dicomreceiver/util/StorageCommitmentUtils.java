package com.eh.digitalpathology.dicomreceiver.util;

import com.eh.digitalpathology.dicomreceiver.model.StorageCommitmentRequest;
import com.eh.digitalpathology.dicomreceiver.model.StorageCommitmentTracker;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.VR;

import java.util.Date;
import java.util.List;

public class StorageCommitmentUtils {

    private StorageCommitmentUtils(){}

    public static Attributes createSuccessItem(String sopClassUID, String sopInstanceUID) {
        Attributes item = new Attributes();
        item.setString(org.dcm4che3.data.Tag.ReferencedSOPClassUID, VR.UI, sopClassUID);
        item.setString(org.dcm4che3.data.Tag.ReferencedSOPInstanceUID, VR.UI, sopInstanceUID);
        return item;
    }

    public static Attributes createFailedItem(String sopClassUID, String sopInstanceUID) {
        Attributes item = new Attributes();
        item.setString(org.dcm4che3.data.Tag.ReferencedSOPClassUID, VR.UI, sopClassUID);
        item.setString(org.dcm4che3.data.Tag.ReferencedSOPInstanceUID, VR.UI, sopInstanceUID);
        item.setInt(org.dcm4che3.data.Tag.FailureReason, VR.US, 0x0110); // Processing failure
        return item;
    }

    public static StorageCommitmentTracker buildTracker(String seriesId, String sopInstanceUID, boolean present,String transactionUID) {
        return new StorageCommitmentTracker( sopInstanceUID, seriesId, true, present, new Date(  ), transactionUID );
    }
    public static StorageCommitmentRequest buildCommitRequest(String seriesId, List<StorageCommitmentTracker> trackers, boolean status) {
        return new StorageCommitmentRequest( seriesId, status, trackers );
    }

}
