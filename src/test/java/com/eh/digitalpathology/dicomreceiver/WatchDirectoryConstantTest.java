package com.eh.digitalpathology.dicomreceiver;

import com.eh.digitalpathology.dicomreceiver.constants.WatchDirectoryConstant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

class WatchDirectoryConstantTest {

    @Test
    void constants_shouldHaveExpectedValues() {
        assertEquals("jcifs.smb.client.minVersion", WatchDirectoryConstant.SMB_MIN_VERSION);
        assertEquals("jcifs.smb.client.maxVersion", WatchDirectoryConstant.SMB_MAX_VERSION);
        assertEquals("smb", WatchDirectoryConstant.SMB);
        assertEquals("Barcode", WatchDirectoryConstant.BARCODE);
    }

    @Test
    void constructor_shouldBePrivate_andThrowOnInvocation() throws Exception {
        Constructor<WatchDirectoryConstant> constructor =
                WatchDirectoryConstant.class.getDeclaredConstructor();

        // Ensure the constructor is private
        assertTrue(Modifier.isPrivate(constructor.getModifiers()),
                "Constructor should be private");

        // Make it accessible to try instantiation
        constructor.setAccessible(true);

        // Attempt to invoke the constructor and expect UnsupportedOperationException
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, constructor::newInstance);
        assertNotNull(thrown.getCause(), "Cause should not be null");
        assertTrue(thrown.getCause() instanceof UnsupportedOperationException,
                "Cause should be UnsupportedOperationException");
        assertEquals("This is a utility class and cannot be instantiated",
                thrown.getCause().getMessage());
    }
}
