package com.eh.digitalpathology.dicomreceiver.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class CommonUtilsTest {

    @InjectMocks
    private CommonUtils commonUtils;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(commonUtils, "fileStorePath", "testDir");
        ReflectionTestUtils.setField(commonUtils, "intermediateFileServer", "/tmp/intermediate");
        ReflectionTestUtils.setField(commonUtils, "enableRemoteDirectoryWatcher", true);
        ReflectionTestUtils.setField(commonUtils, "aeName", "TEST_AE");
        ReflectionTestUtils.setField(commonUtils, "port", 11112);
    }

    @Test
    void testGetLocalStoragePath_CreatesDirectoryAndReturnsPath() {
        String path = commonUtils.getLocalStoragePath();
        assertNotNull(path);
        assertTrue(Files.exists(Path.of(path)));
    }

    @Test
    void testGetterMethods() {
        assertEquals("/tmp/intermediate", commonUtils.getIntermediateFileServer());
        assertTrue(commonUtils.isEnableRemoteDirectoryWatcher());
        assertEquals("TEST_AE", commonUtils.getAeName());
        assertEquals(11112, commonUtils.getPort());
    }

    @Test
    void testGetLocalStoragePath_IOExceptionOccurs() {
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.createDirectories(any(Path.class)))
                    .thenThrow(new IOException());

            ReflectionTestUtils.setField(commonUtils, "fileStorePath", "/invalid/path");

            String result = commonUtils.getLocalStoragePath();
            assertNotNull(result);

        }
    }


}