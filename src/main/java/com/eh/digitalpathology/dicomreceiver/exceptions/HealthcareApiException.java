/**
 * Custom exception class to handle errors related to healthcare API interactions.
 * This exception is thrown when there are issues encountered while communicating
 * with DICOM or HL7 APIs, encapsulating error messages and underlying causes.
 * Author: [Pooja Kamble]
 * Date: October 30, 2024
 * */

package com.eh.digitalpathology.dicomreceiver.exceptions;

public class HealthcareApiException extends RuntimeException  {
    public HealthcareApiException ( String message) {
        super(message);
    }

    public HealthcareApiException ( String message, Throwable cause) {
        super(message, cause);
    }
}
