package com.eh.digitalpathology.dicomreceiver.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SgmtStatus(String cmtReqId, String seriesInstanceUid, String status) {}
