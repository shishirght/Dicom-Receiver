package com.eh.digitalpathology.dicomreceiver.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthcareApiExceptionTest {

    @Test
    @DisplayName("Should set message via single-arg constructor")
    void testMessageConstructor() {
        String message = "HL7 API rate limit exceeded";

        HealthcareApiException ex = new HealthcareApiException(message);

        assertNotNull(ex);
        assertEquals(message, ex.getMessage());
        assertNull(ex.getCause());  // no cause in this constructor
        assertTrue(ex.toString().contains("HealthcareApiException"));
        assertTrue(ex.toString().contains(message));
    }

    @Test
    @DisplayName("Should set message and cause via two-arg constructor")
    void testMessageAndCauseConstructor() {
        String message = "DICOM API timeout";
        Throwable cause = new RuntimeException("Upstream service did not respond");

        HealthcareApiException ex = new HealthcareApiException(message, cause);

        assertNotNull(ex);
        assertEquals(message, ex.getMessage());
        assertSame(cause, ex.getCause());  // ensure exact cause object is preserved
        assertTrue(ex.toString().contains("HealthcareApiException"));
        assertTrue(ex.toString().contains(message));
    }

    @Test
    @DisplayName("Should handle null message and null cause")
    void testNulls() {
        HealthcareApiException ex1 = new HealthcareApiException(null);
        assertNull(ex1.getMessage());
        assertNull(ex1.getCause());
        assertTrue(ex1.toString().contains("HealthcareApiException"));

        HealthcareApiException ex2 = new HealthcareApiException(null, null);
        assertNull(ex2.getMessage());
        assertNull(ex2.getCause());
        assertTrue(ex2.toString().contains("HealthcareApiException"));
    }

    @Test
    @DisplayName("Should be throwable and catchable as RuntimeException")
    void testThrowCatchBehavior() {
        RuntimeException caught = assertThrows(RuntimeException.class, () -> {
            throw new HealthcareApiException("Bad gateway from HIS");
        });
        assertTrue(caught instanceof HealthcareApiException);
        assertEquals("Bad gateway from HIS", caught.getMessage());
    }
}