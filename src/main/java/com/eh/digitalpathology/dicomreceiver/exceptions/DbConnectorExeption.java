package com.eh.digitalpathology.dicomreceiver.exceptions;

public class DbConnectorExeption extends RuntimeException{

    private final String errorCode;

    private final String errorMessage;

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public DbConnectorExeption (String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.errorMessage = message;
    }
}
