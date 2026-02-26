package com.eh.digitalpathology.dicomreceiver.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DbConnectorExeptionTest {

    @Test
    @DisplayName("Should correctly set and return errorCode and errorMessage")
    void testConstructorAndGetters() {
        String errorCode = "DB001";
        String message = "Database unreachable";

        DbConnectorExeption ex = new DbConnectorExeption(errorCode, message);

        assertNotNull(ex);
        assertEquals(errorCode, ex.getErrorCode());
        assertEquals(message, ex.getErrorMessage());
        assertEquals(message, ex.getMessage());   // from RuntimeException
    }

    @Test
    @DisplayName("Should throw DbConnectorExeption with correct message")
    void testExceptionThrowing() {
        String errorCode = "DB002";
        String message = "Connection timeout";

        DbConnectorExeption thrown = assertThrows(DbConnectorExeption.class, () -> {
            throw new DbConnectorExeption(errorCode, message);
        });

        assertEquals(errorCode, thrown.getErrorCode());
        assertEquals(message, thrown.getErrorMessage());
        assertEquals(message, thrown.getMessage());
    }

    @Test
    @DisplayName("Should verify toString() contains class name and message")
    void testToStringContent() {
        String errorCode = "DB003";
        String message = "Driver not found";

        DbConnectorExeption ex = new DbConnectorExeption(errorCode, message);

        String toString = ex.toString();
        assertTrue(toString.contains("DbConnectorExeption"));
        assertTrue(toString.contains(message));
    }
}