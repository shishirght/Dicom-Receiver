package com.eh.digitalpathology.dicomreceiver.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    @DisplayName("Should hold all components and expose them via record accessors")
    void testRecordComponents() {
        ApiResponse<String> ok = new ApiResponse<>("OK", "payload", null, null);

        assertEquals("OK", ok.status());
        assertEquals("payload", ok.content());
        assertNull(ok.errorCode());
        assertNull(ok.errorMessage());
    }

    @Test
    @DisplayName("Equality and hashCode: identical content across different instances")
    void testEqualityAndHashCode() {
        ApiResponse<Integer> a = new ApiResponse<>("OK", 42, null, null);
        ApiResponse<Integer> b = new ApiResponse<>("OK", 42, null, null);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, new ApiResponse<>("ERR", 42, "E001", "Failure"));
    }

    @Test
    @DisplayName("toString: should include field names and values")
    void testToStringFormat() {
        ApiResponse<String> resp = new ApiResponse<>("ERR", null, "E001", "Something went wrong");
        String ts = resp.toString();

        assertTrue(ts.contains("status=ERR"));
        assertTrue(ts.contains("content=null"));
        assertTrue(ts.contains("errorCode=E001"));
        assertTrue(ts.contains("errorMessage=Something went wrong"));
    }

    @Test
    @DisplayName("Generics: should allow arbitrary content types")
    void testGenericsSupport() {
        record Payload(String id, int count) {}
        Payload p = new Payload("X", 7);

        ApiResponse<Payload> resp = new ApiResponse<>("OK", p, null, null);
        assertEquals("OK", resp.status());
        assertEquals(p, resp.content());
        assertEquals(new Payload("X", 7), resp.content()); // record equality
    }
}