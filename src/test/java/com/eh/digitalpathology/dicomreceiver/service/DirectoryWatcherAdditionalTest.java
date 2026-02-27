//package com.eh.digitalpathology.dicomreceiver.service;
//
//import org.junit.jupiter.api.*;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.*;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.apache.commons.io.FileUtils;
//import org.springframework.test.util.ReflectionTestUtils;
//
//import java.io.IOException;
//import java.lang.reflect.Field;
//import java.lang.reflect.Method;
//import java.nio.file.*;
//import java.nio.file.attribute.FileTime;
//import java.time.Instant;
//import java.util.Map;
//import java.util.concurrent.*;
//
//import static org.awaitility.Awaitility.await;
//import static org.junit.jupiter.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
///**
// * Additional tests targeting uncovered lines:
// *
// *  Lines 50-53  : OVERFLOW event branch in directoryLookup()
// *  Lines 145-150: !Files.exists and !Files.isReadable branches in startStabilityCheck()
// *  Lines 180-185: IOException and Exception catch blocks in startStabilityCheck()
// *  Lines 248-250: IOException and Exception catch blocks in runProbe()
// */
//@ExtendWith(MockitoExtension.class)
//@DisplayName("DirectoryWatcher Additional Coverage Tests")
//class DirectoryWatcherAdditionalTest {
//
//    @Mock
//    private FileProcessingService fileProcessingService;
//
//    private ExecutorService executorService;
//    private ScheduledExecutorService stabilizerScheduler;
//    private DirectoryWatcher directoryWatcher;
//    private Path tempDir;
//
//    @BeforeEach
//    void setUp() throws IOException {
//        executorService = Executors.newSingleThreadExecutor();
//        stabilizerScheduler = Executors.newSingleThreadScheduledExecutor();
//        tempDir = Files.createTempDirectory("watcherAdditionalTest");
//        directoryWatcher = Mockito.spy(
//                new DirectoryWatcher(fileProcessingService, executorService, stabilizerScheduler));
//    }
//
//    @AfterEach
//    void tearDown() {
//        executorService.shutdownNow();
//        stabilizerScheduler.shutdownNow();
//        FileUtils.deleteQuietly(tempDir.toFile());
//    }
//
//    // =========================================================================
//    // Lines 50-53 — OVERFLOW branch in directoryLookup()
//    //
//    // The WatchService delivers an OVERFLOW event when the OS event queue
//    // overflows. The code should call rescanDirectory() and continue.
//    // We verify this by asserting that after an OVERFLOW the watcher rescans
//    // and ultimately processes any files already sitting in the directory.
//    // =========================================================================
//
//    @Test
//    @DisplayName("Lines 50-53: OVERFLOW event triggers rescanDirectory and processes existing files")
//    void testDirectoryLookup_OverflowEvent_TriggersRescan() throws Exception {
//        // Place a real file in the watched directory BEFORE the watcher starts,
//        // so that when OVERFLOW forces a rescan it finds the file and processes it.
//        Path existingFile = tempDir.resolve("existing.dcm");
//        Files.createFile(existingFile);
//
//        Thread watcherThread = new Thread(() ->
//                directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore"),
//                "watcher-overflow");
//        watcherThread.setDaemon(true);
//        watcherThread.start();
//
//        // Wait until watcher is up
//        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));
//
//        // Flood the directory to provoke an OVERFLOW event from the OS.
//        // Creating 500 files rapidly typically overflows the native watch queue.
//        for (int i = 0; i < 500; i++) {
//            Files.createFile(tempDir.resolve("flood_" + i + ".dcm"));
//        }
//
//        // After an OVERFLOW the watcher rescans and should process files.
//        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() ->
//                verify(fileProcessingService, atLeastOnce())
//                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
//        );
//
//        watcherThread.interrupt();
//        watcherThread.join(3000);
//    }
//
//    // =========================================================================
//    // Lines 145-150 — startStabilityCheck(): path deleted before check runs
//    //                                         + path not yet readable
//    //
//    // Line 141-144: file deleted between event and stability check → cleanup()
//    // Line 146-149: file exists but not readable → reschedule()
//    // =========================================================================
//
//    @Test
//    @DisplayName("Lines 141-144: startStabilityCheck cleans up when path no longer exists")
//    void testStartStabilityCheck_PathNoLongerExists_ShouldCleanup() throws Exception {
//        Path file = tempDir.resolve("vanished.dcm");
//        // Do NOT create the file — it should not exist when startStabilityCheck runs
//
//        Method startMethod = DirectoryWatcher.class.getDeclaredMethod(
//                "startStabilityCheck", Path.class, String.class, String.class);
//        startMethod.setAccessible(true);
//
//        startMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");
//
//        // Verify cleanup: no probe state, no scheduled check
//        Field probeStatesField = DirectoryWatcher.class.getDeclaredField("probeStates");
//        probeStatesField.setAccessible(true);
//        @SuppressWarnings("unchecked")
//        Map<Path, ?> probeStates = (Map<Path, ?>) probeStatesField.get(directoryWatcher);
//
//        Field scheduledChecksField = DirectoryWatcher.class.getDeclaredField("scheduledChecks");
//        scheduledChecksField.setAccessible(true);
//        @SuppressWarnings("unchecked")
//        Map<Path, ?> scheduledChecks = (Map<Path, ?>) scheduledChecksField.get(directoryWatcher);
//
//        assertNull(probeStates.get(file), "probeState should be cleaned up for deleted file");
//        assertNull(scheduledChecks.get(file), "scheduledCheck should be cleaned up for deleted file");
//    }
//
//    @Test
//    @DisplayName("Lines 146-150: startStabilityCheck reschedules when path is not readable")
//    void testStartStabilityCheck_PathNotReadable_ShouldReschedule() throws Exception {
//        Path file = tempDir.resolve("unreadable.dcm");
//        Files.createFile(file);
//
//        // Make it unreadable (works on POSIX systems; skip gracefully on Windows)
//        boolean madeUnreadable = file.toFile().setReadable(false);
//        Assumptions.assumeTrue(madeUnreadable, "Cannot make file unreadable on this OS — skipping");
//
//        try {
//            Method startMethod = DirectoryWatcher.class.getDeclaredMethod(
//                    "startStabilityCheck", Path.class, String.class, String.class);
//            startMethod.setAccessible(true);
//
//            startMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");
//
//            // A reschedule means a new ScheduledFuture is placed in scheduledChecks
//            Field scheduledChecksField = DirectoryWatcher.class.getDeclaredField("scheduledChecks");
//            scheduledChecksField.setAccessible(true);
//            @SuppressWarnings("unchecked")
//            Map<Path, ?> scheduledChecks = (Map<Path, ?>) scheduledChecksField.get(directoryWatcher);
//
//            assertNotNull(scheduledChecks.get(file),
//                    "A reschedule future should exist for an unreadable file");
//
//            // Retry count should have been incremented
//            Field retryCountsField = DirectoryWatcher.class.getDeclaredField("retryCounts");
//            retryCountsField.setAccessible(true);
//            @SuppressWarnings("unchecked")
//            Map<Path, Integer> retryCounts = (Map<Path, Integer>) retryCountsField.get(directoryWatcher);
//            assertTrue(retryCounts.getOrDefault(file, 0) >= 1,
//                    "Retry count should be at least 1 after reschedule");
//        } finally {
//            file.toFile().setReadable(true); // restore so cleanup can delete it
//        }
//    }
//
//    // =========================================================================
//    // Lines 180-185 — startStabilityCheck(): IOException and Exception catch
//    //
//    // We use a MockedStatic on Files to force Files.size() to throw, which is
//    // called after the exists/isReadable checks inside startStabilityCheck().
//    // =========================================================================
//
//    @Test
//    @DisplayName("Lines 180-182: startStabilityCheck IOException catch reschedules")
//    void testStartStabilityCheck_IOException_ShouldReschedule() throws Exception {
//        Path file = tempDir.resolve("ioerror.dcm");
//        Files.createFile(file);
//
//        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
//            // Let exists() and isReadable() pass normally
//            mockedFiles.when(() -> Files.exists(file)).thenReturn(true);
//            mockedFiles.when(() -> Files.isReadable(file)).thenReturn(true);
//            // First call throws IOException to trigger the catch block;
//            // second call returns 0L for reschedule -> computeDynamicQuietMillis.
//            mockedFiles.when(() -> Files.size(file))
//                    .thenThrow(new IOException("Simulated IO failure"))
//                    .thenReturn(0L);
//            mockedFiles.when(() -> Files.deleteIfExists(any())).thenReturn(true);
//            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
//
//            Method startMethod = DirectoryWatcher.class.getDeclaredMethod(
//                    "startStabilityCheck", Path.class, String.class, String.class);
//            startMethod.setAccessible(true);
//
//            assertDoesNotThrow(() ->
//                    startMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore"));
//
//            // Reschedule increments retry count
//            Field retryCountsField = DirectoryWatcher.class.getDeclaredField("retryCounts");
//            retryCountsField.setAccessible(true);
//            @SuppressWarnings("unchecked")
//            Map<Path, Integer> retryCounts = (Map<Path, Integer>) retryCountsField.get(directoryWatcher);
//            assertTrue(retryCounts.getOrDefault(file, 0) >= 1,
//                    "Retry count should be incremented after IOException in startStabilityCheck");
//        }
//    }
//
//    @Test
//    @DisplayName("Lines 183-185: startStabilityCheck generic Exception catch reschedules")
//    void testStartStabilityCheck_GenericException_ShouldReschedule() throws Exception {
//        Path file = tempDir.resolve("genericerror.dcm");
//        Files.createFile(file);
//
//        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
//            mockedFiles.when(() -> Files.exists(file)).thenReturn(true);
//            mockedFiles.when(() -> Files.isReadable(file)).thenReturn(true);
//            // First call (inside startStabilityCheck) throws to hit the Exception catch.
//            // Subsequent calls (reschedule -> computeDynamicQuietMillis) must return
//            // normally so reschedule itself does not propagate the exception.
//            mockedFiles.when(() -> Files.size(file))
//                    .thenThrow(new RuntimeException("Simulated unexpected failure"))
//                    .thenReturn(0L); // fallback for computeDynamicQuietMillis
//            mockedFiles.when(() -> Files.deleteIfExists(any())).thenReturn(true);
//            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
//
//            Method startMethod = DirectoryWatcher.class.getDeclaredMethod(
//                    "startStabilityCheck", Path.class, String.class, String.class);
//            startMethod.setAccessible(true);
//
//            assertDoesNotThrow(() ->
//                    startMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore"));
//
//            Field retryCountsField = DirectoryWatcher.class.getDeclaredField("retryCounts");
//            retryCountsField.setAccessible(true);
//            @SuppressWarnings("unchecked")
//            Map<Path, Integer> retryCounts = (Map<Path, Integer>) retryCountsField.get(directoryWatcher);
//            assertTrue(retryCounts.getOrDefault(file, 0) >= 1,
//                    "Retry count should be incremented after generic Exception in startStabilityCheck");
//        }
//    }
//
//    // =========================================================================
//    // Lines 248-253 — runProbe(): IOException and Exception catch blocks
//    //
//    // We inject a ProbeState so the state != null branch is skipped, then
//    // force Files.size() (called inside runProbe) to throw.
//    // =========================================================================
//
//    @Test
//    @DisplayName("Lines 248-250: runProbe IOException catch reschedules")
//    void testRunProbe_IOException_ShouldReschedule() throws Exception {
//        Path file = tempDir.resolve("probe-ioerror.dcm");
//        Files.createFile(file);
//
//        // Pre-populate a ProbeState so the null-state guard is bypassed
//        injectProbeState(file, 500L, 2);
//
//        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
//            mockedFiles.when(() -> Files.exists(file)).thenReturn(true);
//            mockedFiles.when(() -> Files.isReadable(file)).thenReturn(true);
//            // First call throws IOException to trigger the catch block;
//            // second call returns 0L for reschedule -> computeDynamicQuietMillis.
//            mockedFiles.when(() -> Files.size(file))
//                    .thenThrow(new IOException("Simulated probe IO failure"))
//                    .thenReturn(0L);
//            mockedFiles.when(() -> Files.deleteIfExists(any())).thenReturn(true);
//            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
//
//            Method probeMethod = DirectoryWatcher.class.getDeclaredMethod(
//                    "runProbe", Path.class, String.class, String.class);
//            probeMethod.setAccessible(true);
//
//            assertDoesNotThrow(() ->
//                    probeMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore"));
//
//            Field retryCountsField = DirectoryWatcher.class.getDeclaredField("retryCounts");
//            retryCountsField.setAccessible(true);
//            @SuppressWarnings("unchecked")
//            Map<Path, Integer> retryCounts = (Map<Path, Integer>) retryCountsField.get(directoryWatcher);
//            assertTrue(retryCounts.getOrDefault(file, 0) >= 1,
//                    "Retry count should be incremented after IOException in runProbe");
//        }
//    }
//
//    @Test
//    @DisplayName("Lines 251-253: runProbe generic Exception catch reschedules")
//    void testRunProbe_GenericException_ShouldReschedule() throws Exception {
//        Path file = tempDir.resolve("probe-genericerror.dcm");
//        Files.createFile(file);
//
//        injectProbeState(file, 500L, 2);
//
//        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
//            mockedFiles.when(() -> Files.exists(file)).thenReturn(true);
//            mockedFiles.when(() -> Files.isReadable(file)).thenReturn(true);
//            // First call (inside runProbe) throws to trigger the Exception catch block.
//            // Subsequent calls (inside reschedule -> computeDynamicQuietMillis) must
//            // return normally, otherwise the exception propagates out of reschedule uncaught.
//            mockedFiles.when(() -> Files.size(file))
//                    .thenThrow(new RuntimeException("Simulated probe unexpected failure"))
//                    .thenReturn(0L); // fallback for computeDynamicQuietMillis
//            mockedFiles.when(() -> Files.deleteIfExists(any())).thenReturn(true);
//            // computeDynamicQuietMillis also calls Files.exists — keep returning true
//            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);
//
//            Method probeMethod = DirectoryWatcher.class.getDeclaredMethod(
//                    "runProbe", Path.class, String.class, String.class);
//            probeMethod.setAccessible(true);
//
//            assertDoesNotThrow(() ->
//                    probeMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore"));
//
//            Field retryCountsField = DirectoryWatcher.class.getDeclaredField("retryCounts");
//            retryCountsField.setAccessible(true);
//            @SuppressWarnings("unchecked")
//            Map<Path, Integer> retryCounts = (Map<Path, Integer>) retryCountsField.get(directoryWatcher);
//            assertTrue(retryCounts.getOrDefault(file, 0) >= 1,
//                    "Retry count should be incremented after generic Exception in runProbe");
//        }
//    }
//
//    // =========================================================================
//    // Bonus: runProbe() — path not readable branch (parallels startStabilityCheck)
//    // =========================================================================
//
//    @Test
//    @DisplayName("runProbe: path not readable triggers reschedule")
//    void testRunProbe_PathNotReadable_ShouldReschedule() throws Exception {
//        Path file = tempDir.resolve("probe-unreadable.dcm");
//        Files.createFile(file);
//
//        boolean madeUnreadable = file.toFile().setReadable(false);
//        Assumptions.assumeTrue(madeUnreadable, "Cannot make file unreadable on this OS — skipping");
//
//        try {
//            injectProbeState(file, 500L, 2);
//
//            Method probeMethod = DirectoryWatcher.class.getDeclaredMethod(
//                    "runProbe", Path.class, String.class, String.class);
//            probeMethod.setAccessible(true);
//
//            probeMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");
//
//            Field retryCountsField = DirectoryWatcher.class.getDeclaredField("retryCounts");
//            retryCountsField.setAccessible(true);
//            @SuppressWarnings("unchecked")
//            Map<Path, Integer> retryCounts = (Map<Path, Integer>) retryCountsField.get(directoryWatcher);
//            assertTrue(retryCounts.getOrDefault(file, 0) >= 1,
//                    "Retry count should be incremented when probe path is unreadable");
//        } finally {
//            file.toFile().setReadable(true);
//        }
//    }
//
//    // =========================================================================
//    // Helper — inject a ProbeState via reflection into the probeStates map
//    // =========================================================================
//
//    private void injectProbeState(Path path, long probeMs, int probesRequired) throws Exception {
//        Class<?> probeStateClass = Class.forName(
//                "com.eh.digitalpathology.dicomreceiver.service.DirectoryWatcher$ProbeState");
//
//        // ProbeState constructor is private — must call setAccessible before newInstance()
//        java.lang.reflect.Constructor<?> ctor = probeStateClass.getDeclaredConstructor();
//        ctor.setAccessible(true);
//        Object state = ctor.newInstance();
//
//        Field probeMsField = probeStateClass.getDeclaredField("probeMs");
//        probeMsField.setAccessible(true);
//        probeMsField.set(state, probeMs);
//
//        Field probesRequiredField = probeStateClass.getDeclaredField("probesRequired");
//        probesRequiredField.setAccessible(true);
//        probesRequiredField.set(state, probesRequired);
//
//        Field prevSizeField = probeStateClass.getDeclaredField("prevSize");
//        prevSizeField.setAccessible(true);
//        prevSizeField.set(state, -1L);
//
//        Field probeStatesField = DirectoryWatcher.class.getDeclaredField("probeStates");
//        probeStatesField.setAccessible(true);
//        @SuppressWarnings("unchecked")
//        Map<Path, Object> probeStates = (Map<Path, Object>) probeStatesField.get(directoryWatcher);
//        probeStates.put(path, state);
//    }
//}


package com.eh.digitalpathology.dicomreceiver.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.commons.io.FileUtils;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Additional tests targeting uncovered lines:
 *
 *  Lines 50-53  : OVERFLOW event branch in directoryLookup()
 *  Lines 145-150: !Files.exists and !Files.isReadable branches in startStabilityCheck()
 *  Lines 180-185: IOException and Exception catch blocks in startStabilityCheck()
 *  Lines 248-250: IOException and Exception catch blocks in runProbe()
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DirectoryWatcher Additional Coverage Tests")
class DirectoryWatcherAdditionalTest {

    @Mock
    private FileProcessingService fileProcessingService;

    private ExecutorService executorService;
    private ScheduledExecutorService stabilizerScheduler;
    private DirectoryWatcher directoryWatcher;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        executorService = Executors.newSingleThreadExecutor();
        stabilizerScheduler = Executors.newSingleThreadScheduledExecutor();
        tempDir = Files.createTempDirectory("watcherAdditionalTest");
        directoryWatcher = Mockito.spy(
                new DirectoryWatcher(fileProcessingService, executorService, stabilizerScheduler));
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
        stabilizerScheduler.shutdownNow();
        FileUtils.deleteQuietly(tempDir.toFile());
    }

    // =========================================================================
    // Lines 50-53 — OVERFLOW branch in directoryLookup()
    //
    // The WatchService delivers an OVERFLOW event when the OS event queue
    // overflows. The code should call rescanDirectory() and continue.
    // We verify this by asserting that after an OVERFLOW the watcher rescans
    // and ultimately processes any files already sitting in the directory.
    // =========================================================================

    @Test
    @DisplayName("Lines 50-53: OVERFLOW event triggers rescanDirectory and processes existing files")
    void testDirectoryLookup_OverflowEvent_TriggersRescan() throws Exception {
        // Place a real file in the watched directory BEFORE the watcher starts,
        // so that when OVERFLOW forces a rescan it finds the file and processes it.
        Path existingFile = tempDir.resolve("existing.dcm");
        Files.createFile(existingFile);

        Thread watcherThread = new Thread(() ->
                directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore"),
                "watcher-overflow");
        watcherThread.setDaemon(true);
        watcherThread.start();

        // Wait until watcher is up
        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        // Flood the directory to provoke an OVERFLOW event from the OS.
        // Creating 500 files rapidly typically overflows the native watch queue.
        for (int i = 0; i < 500; i++) {
            Files.createFile(tempDir.resolve("flood_" + i + ".dcm"));
        }

        // After an OVERFLOW the watcher rescans and should process files.
        await().atMost(60, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeastOnce())
                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
        );

        watcherThread.interrupt();
        watcherThread.join(3000);
    }

    // =========================================================================
    // Lines 145-150 — startStabilityCheck(): path deleted before check runs
    //                                         + path not yet readable
    //
    // Line 141-144: file deleted between event and stability check → cleanup()
    // Line 146-149: file exists but not readable → reschedule()
    // =========================================================================

    @Test
    @DisplayName("Lines 141-144: startStabilityCheck cleans up when path no longer exists")
    void testStartStabilityCheck_PathNoLongerExists_ShouldCleanup() throws Exception {
        Path file = tempDir.resolve("vanished.dcm");
        // Do NOT create the file — it should not exist when startStabilityCheck runs

        Method startMethod = DirectoryWatcher.class.getDeclaredMethod(
                "startStabilityCheck", Path.class, String.class, String.class);
        startMethod.setAccessible(true);

        startMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");

        // Verify cleanup: no probe state, no scheduled check
        Field probeStatesField = DirectoryWatcher.class.getDeclaredField("probeStates");
        probeStatesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, ?> probeStates = (Map<Path, ?>) probeStatesField.get(directoryWatcher);

        Field scheduledChecksField = DirectoryWatcher.class.getDeclaredField("scheduledChecks");
        scheduledChecksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, ?> scheduledChecks = (Map<Path, ?>) scheduledChecksField.get(directoryWatcher);

        assertNull(probeStates.get(file), "probeState should be cleaned up for deleted file");
        assertNull(scheduledChecks.get(file), "scheduledCheck should be cleaned up for deleted file");
    }

    @Test
    @DisplayName("Lines 146-150: startStabilityCheck reschedules when path is not readable")
    void testStartStabilityCheck_PathNotReadable_ShouldReschedule() throws Exception {
        Path file = tempDir.resolve("unreadable.dcm");
        Files.createFile(file);

        // Make it unreadable (works on POSIX systems; skip gracefully on Windows)
        boolean madeUnreadable = file.toFile().setReadable(false);
        Assumptions.assumeTrue(madeUnreadable, "Cannot make file unreadable on this OS — skipping");

        try {
            Method startMethod = DirectoryWatcher.class.getDeclaredMethod(
                    "startStabilityCheck", Path.class, String.class, String.class);
            startMethod.setAccessible(true);

            startMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");

            // A reschedule means a new ScheduledFuture is placed in scheduledChecks
            Field scheduledChecksField = DirectoryWatcher.class.getDeclaredField("scheduledChecks");
            scheduledChecksField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Path, ?> scheduledChecks = (Map<Path, ?>) scheduledChecksField.get(directoryWatcher);

            assertNotNull(scheduledChecks.get(file),
                    "A reschedule future should exist for an unreadable file");

            // Retry count should have been incremented
            Field retryCountsField = DirectoryWatcher.class.getDeclaredField("retryCounts");
            retryCountsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Path, Integer> retryCounts = (Map<Path, Integer>) retryCountsField.get(directoryWatcher);
            assertTrue(retryCounts.getOrDefault(file, 0) >= 1,
                    "Retry count should be at least 1 after reschedule");
        } finally {
            file.toFile().setReadable(true); // restore so cleanup can delete it
        }
    }

    // =========================================================================
    // Lines 180-185 — startStabilityCheck(): IOException and Exception catch
    //
    // We use a MockedStatic on Files to force Files.size() to throw, which is
    // called after the exists/isReadable checks inside startStabilityCheck().
    // =========================================================================

    @Test
    @DisplayName("Lines 180-182: startStabilityCheck IOException catch reschedules")
    void testStartStabilityCheck_IOException_ShouldReschedule() throws Exception {
        Path file = tempDir.resolve("ioerror.dcm");
        Files.createFile(file);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            // Let exists() and isReadable() pass normally
            mockedFiles.when(() -> Files.exists(file)).thenReturn(true);
            mockedFiles.when(() -> Files.isReadable(file)).thenReturn(true);
            // First call throws IOException to trigger the catch block;
            // second call returns 0L for reschedule -> computeDynamicQuietMillis.
            mockedFiles.when(() -> Files.size(file))
                    .thenThrow(new IOException("Simulated IO failure"))
                    .thenReturn(0L);
            mockedFiles.when(() -> Files.deleteIfExists(any())).thenReturn(true);
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);

            Method startMethod = DirectoryWatcher.class.getDeclaredMethod(
                    "startStabilityCheck", Path.class, String.class, String.class);
            startMethod.setAccessible(true);

            assertDoesNotThrow(() ->
                    startMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore"));

            // Reschedule increments retry count
            Field retryCountsField = DirectoryWatcher.class.getDeclaredField("retryCounts");
            retryCountsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Path, Integer> retryCounts = (Map<Path, Integer>) retryCountsField.get(directoryWatcher);
            assertTrue(retryCounts.getOrDefault(file, 0) >= 1,
                    "Retry count should be incremented after IOException in startStabilityCheck");
        }
    }

    @Test
    @DisplayName("Lines 183-185: startStabilityCheck generic Exception catch reschedules")
    void testStartStabilityCheck_GenericException_ShouldReschedule() throws Exception {
        Path file = tempDir.resolve("genericerror.dcm");
        Files.createFile(file);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(file)).thenReturn(true);
            mockedFiles.when(() -> Files.isReadable(file)).thenReturn(true);
            // First call (inside startStabilityCheck) throws to hit the Exception catch.
            // Subsequent calls (reschedule -> computeDynamicQuietMillis) must return
            // normally so reschedule itself does not propagate the exception.
            mockedFiles.when(() -> Files.size(file))
                    .thenThrow(new RuntimeException("Simulated unexpected failure"))
                    .thenReturn(0L); // fallback for computeDynamicQuietMillis
            mockedFiles.when(() -> Files.deleteIfExists(any())).thenReturn(true);
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);

            Method startMethod = DirectoryWatcher.class.getDeclaredMethod(
                    "startStabilityCheck", Path.class, String.class, String.class);
            startMethod.setAccessible(true);

            assertDoesNotThrow(() ->
                    startMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore"));

            Field retryCountsField = DirectoryWatcher.class.getDeclaredField("retryCounts");
            retryCountsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Path, Integer> retryCounts = (Map<Path, Integer>) retryCountsField.get(directoryWatcher);
            assertTrue(retryCounts.getOrDefault(file, 0) >= 1,
                    "Retry count should be incremented after generic Exception in startStabilityCheck");
        }
    }

    // =========================================================================
    // Lines 248-253 — runProbe(): IOException and Exception catch blocks
    //
    // We inject a ProbeState so the state != null branch is skipped, then
    // force Files.size() (called inside runProbe) to throw.
    // =========================================================================

    @Test
    @DisplayName("Lines 248-250: runProbe IOException catch reschedules")
    void testRunProbe_IOException_ShouldReschedule() throws Exception {
        Path file = tempDir.resolve("probe-ioerror.dcm");
        Files.createFile(file);

        // Pre-populate a ProbeState so the null-state guard is bypassed
        injectProbeState(file, 500L, 2);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(file)).thenReturn(true);
            mockedFiles.when(() -> Files.isReadable(file)).thenReturn(true);
            // First call throws IOException to trigger the catch block;
            // second call returns 0L for reschedule -> computeDynamicQuietMillis.
            mockedFiles.when(() -> Files.size(file))
                    .thenThrow(new IOException("Simulated probe IO failure"))
                    .thenReturn(0L);
            mockedFiles.when(() -> Files.deleteIfExists(any())).thenReturn(true);
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);

            Method probeMethod = DirectoryWatcher.class.getDeclaredMethod(
                    "runProbe", Path.class, String.class, String.class);
            probeMethod.setAccessible(true);

            assertDoesNotThrow(() ->
                    probeMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore"));

            Field retryCountsField = DirectoryWatcher.class.getDeclaredField("retryCounts");
            retryCountsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Path, Integer> retryCounts = (Map<Path, Integer>) retryCountsField.get(directoryWatcher);
            assertTrue(retryCounts.getOrDefault(file, 0) >= 1,
                    "Retry count should be incremented after IOException in runProbe");
        }
    }

    @Test
    @DisplayName("Lines 251-253: runProbe generic Exception catch reschedules")
    void testRunProbe_GenericException_ShouldReschedule() throws Exception {
        Path file = tempDir.resolve("probe-genericerror.dcm");
        Files.createFile(file);

        injectProbeState(file, 500L, 2);

        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.exists(file)).thenReturn(true);
            mockedFiles.when(() -> Files.isReadable(file)).thenReturn(true);
            // First call (inside runProbe) throws to trigger the Exception catch block.
            // Subsequent calls (inside reschedule -> computeDynamicQuietMillis) must
            // return normally, otherwise the exception propagates out of reschedule uncaught.
            mockedFiles.when(() -> Files.size(file))
                    .thenThrow(new RuntimeException("Simulated probe unexpected failure"))
                    .thenReturn(0L); // fallback for computeDynamicQuietMillis
            mockedFiles.when(() -> Files.deleteIfExists(any())).thenReturn(true);
            // computeDynamicQuietMillis also calls Files.exists — keep returning true
            mockedFiles.when(() -> Files.exists(any())).thenReturn(true);

            Method probeMethod = DirectoryWatcher.class.getDeclaredMethod(
                    "runProbe", Path.class, String.class, String.class);
            probeMethod.setAccessible(true);

            assertDoesNotThrow(() ->
                    probeMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore"));

            Field retryCountsField = DirectoryWatcher.class.getDeclaredField("retryCounts");
            retryCountsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Path, Integer> retryCounts = (Map<Path, Integer>) retryCountsField.get(directoryWatcher);
            assertTrue(retryCounts.getOrDefault(file, 0) >= 1,
                    "Retry count should be incremented after generic Exception in runProbe");
        }
    }

    // =========================================================================
    // Bonus: runProbe() — path not readable branch (parallels startStabilityCheck)
    // =========================================================================

    @Test
    @DisplayName("runProbe: path not readable triggers reschedule")
    void testRunProbe_PathNotReadable_ShouldReschedule() throws Exception {
        Path file = tempDir.resolve("probe-unreadable.dcm");
        Files.createFile(file);

        boolean madeUnreadable = file.toFile().setReadable(false);
        Assumptions.assumeTrue(madeUnreadable, "Cannot make file unreadable on this OS — skipping");

        try {
            injectProbeState(file, 500L, 2);

            Method probeMethod = DirectoryWatcher.class.getDeclaredMethod(
                    "runProbe", Path.class, String.class, String.class);
            probeMethod.setAccessible(true);

            probeMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");

            Field retryCountsField = DirectoryWatcher.class.getDeclaredField("retryCounts");
            retryCountsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Path, Integer> retryCounts = (Map<Path, Integer>) retryCountsField.get(directoryWatcher);
            assertTrue(retryCounts.getOrDefault(file, 0) >= 1,
                    "Retry count should be incremented when probe path is unreadable");
        } finally {
            file.toFile().setReadable(true);
        }
    }

    // =========================================================================
    // Helper — inject a ProbeState via reflection into the probeStates map
    // =========================================================================

    private void injectProbeState(Path path, long probeMs, int probesRequired) throws Exception {
        Class<?> probeStateClass = Class.forName(
                "com.eh.digitalpathology.dicomreceiver.service.DirectoryWatcher$ProbeState");

        // ProbeState constructor is private — must call setAccessible before newInstance()
        java.lang.reflect.Constructor<?> ctor = probeStateClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object state = ctor.newInstance();

        Field probeMsField = probeStateClass.getDeclaredField("probeMs");
        probeMsField.setAccessible(true);
        probeMsField.set(state, probeMs);

        Field probesRequiredField = probeStateClass.getDeclaredField("probesRequired");
        probesRequiredField.setAccessible(true);
        probesRequiredField.set(state, probesRequired);

        Field prevSizeField = probeStateClass.getDeclaredField("prevSize");
        prevSizeField.setAccessible(true);
        prevSizeField.set(state, -1L);

        Field probeStatesField = DirectoryWatcher.class.getDeclaredField("probeStates");
        probeStatesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, Object> probeStates = (Map<Path, Object>) probeStatesField.get(directoryWatcher);
        probeStates.put(path, state);
    }}