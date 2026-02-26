package com.eh.digitalpathology.dicomreceiver.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DicomAttributesExceptionTest {

    @Test
    @DisplayName("Should correctly set and return errorCode and errorMessage")
    void testConstructorAndGetters() {
        String errorCode = "DICOM_001";
        String message = "Missing required DICOM attribute";

        DicomAttributesException ex = new DicomAttributesException(errorCode, message);

        assertNotNull(ex);
        assertEquals(errorCode, ex.getErrorCode());
        assertEquals(message, ex.getErrorMessage());
        assertEquals(message, ex.getMessage()); // inherited from RuntimeException
    }

    @Test
    @DisplayName("Should throw DicomAttributesException with correct values")
    void testExceptionThrowing() {
        String errorCode = "DICOM_002";
        String message = "Invalid DICOM tag format";

        DicomAttributesException thrown = assertThrows(DicomAttributesException.class, () -> {
            throw new DicomAttributesException(errorCode, message);
        });

        assertEquals(errorCode, thrown.getErrorCode());
        assertEquals(message, thrown.getErrorMessage());
        assertEquals(message, thrown.getMessage());
    }

    @Test
    @DisplayName("toString() should contain class name and message")
    void testToStringContainsDetails() {
        String errorCode = "DICOM_003";
        String message = "Unsupported VR type";

        DicomAttributesException ex = new DicomAttributesException(errorCode, message);

        String ts = ex.toString();
        assertTrue(ts.contains("DicomAttributesException"));
        assertTrue(ts.contains(message));
    }

    @Test
    @DisplayName("Should handle nulls gracefully in constructor")
    void testNullInputs() {
        DicomAttributesException ex = new DicomAttributesException(null, null);

        assertNull(ex.getErrorCode());
        assertNull(ex.getErrorMessage());
        assertNull(ex.getMessage()); // RuntimeException message can be null
        assertTrue(ex.toString().contains("DicomAttributesException"));
    }
}