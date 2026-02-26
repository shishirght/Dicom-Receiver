package com.eh.digitalpathology.dicomreceiver.model;

import java.util.List;

public record StorageCommitmentRequest(String seriesInstanceUid, boolean storageCmtStatus, List<StorageCommitmentTracker> trackers) {}

