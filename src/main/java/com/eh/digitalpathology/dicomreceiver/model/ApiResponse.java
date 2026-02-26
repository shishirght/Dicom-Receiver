package com.eh.digitalpathology.dicomreceiver.model;

public record ApiResponse< T >(
        String status,
        T content,
        String errorCode,
        String errorMessage
){}
