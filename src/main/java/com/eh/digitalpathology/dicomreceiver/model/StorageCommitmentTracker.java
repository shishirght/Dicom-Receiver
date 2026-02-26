package com.eh.digitalpathology.dicomreceiver.model;

import java.util.Date;

public record StorageCommitmentTracker(String sopInstanceUid, String seriesInstanceUid, Boolean cmtRequestStatus, Boolean cmtResponseStatus, Date timestamp, String requestId) {}

