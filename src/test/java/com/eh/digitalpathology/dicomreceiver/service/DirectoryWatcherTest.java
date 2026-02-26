package com.eh.digitalpathology.dicomreceiver.service;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("DirectoryWatcher Tests")
class DirectoryWatcherTest {

    @Mock
    private FileProcessingService fileProcessingService;
    @Mock
    private ExecutorService executorService;
    @Mock
    private ScheduledExecutorService stabilizerScheduler;

    private DirectoryWatcher directoryWatcher;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        executorService = Executors.newSingleThreadExecutor();
        stabilizerScheduler = Executors.newSingleThreadScheduledExecutor();
        tempDir = Files.createTempDirectory("watcherTest");
        DirectoryWatcher realWatcher = new DirectoryWatcher(fileProcessingService, executorService, stabilizerScheduler);
        directoryWatcher = Mockito.spy(realWatcher);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
        stabilizerScheduler.shutdownNow();
        FileUtils.deleteQuietly(tempDir.toFile());
    }

    @Test
    @DisplayName("Should process file when new file is detected")
    void testShouldProcessFileWhenNewFileIsDetected() throws Exception {
        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        }, "watcher-daemon");
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Path newFile = tempDir.resolve("testFile.txt");
        Files.createFile(newFile);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeastOnce())
                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
        );

        watcherThread.interrupt();
        watcherThread.join(2000);
        assertFalse(watcherThread.isAlive(), "Watcher should be terminated");
    }

    @Test
    @DisplayName("Should ignore partial files (.part)")
    void testDirectoryLookup_IgnoresPartialFiles() throws Exception {
        ExecutorService mockExecutor = mock(ExecutorService.class);
        ReflectionTestUtils.setField(directoryWatcher, "executorService", mockExecutor);

        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();
        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Files.createFile(tempDir.resolve("incomplete.part"));
        Files.createFile(tempDir.resolve("inprogress.filepart"));
        await().during(2, TimeUnit.SECONDS).until(() -> true);
        watcherThread.interrupt();
        watcherThread.join(5000);

        verify(mockExecutor, never()).submit(any(Runnable.class));
    }

    @Test
    @DisplayName("Should ignore temporary files (.tmp)")
    void testDirectoryLookup_IgnoresTempFiles() throws Exception {
        ExecutorService mockExecutor = mock(ExecutorService.class);
        ReflectionTestUtils.setField(directoryWatcher, "executorService", mockExecutor);

        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();
        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Files.createFile(tempDir.resolve("temporary.tmp"));
        await().during(2, TimeUnit.SECONDS).until(() -> true);
        watcherThread.interrupt();
        watcherThread.join(5000);

        verify(mockExecutor, never()).submit(any(Runnable.class));
    }

    @Test
    @DisplayName("Should ignore directories")
    void testDirectoryLookup_IgnoresDirectories() throws Exception {
        ExecutorService mockExecutor = mock(ExecutorService.class);
        ReflectionTestUtils.setField(directoryWatcher, "executorService", mockExecutor);

        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();
        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Files.createDirectory(tempDir.resolve("subFolder"));
        await().during(2, TimeUnit.SECONDS).until(() -> true);
        watcherThread.interrupt();
        watcherThread.join(5000);

        verify(mockExecutor, never()).submit(any(Runnable.class));
    }

    @Test
    @DisplayName("Should handle multiple files created in sequence")
    void testDirectoryLookup_MultipleFiles() throws Exception {
        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Path file1 = tempDir.resolve("file1.txt");
        Path file2 = tempDir.resolve("file2.txt");
        Path file3 = tempDir.resolve("file3.txt");

        Files.createFile(file1);
        Thread.sleep(500);
        Files.createFile(file2);
        Thread.sleep(500);
        Files.createFile(file3);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeast(3))
                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
        );

        watcherThread.interrupt();
        watcherThread.join(2000);
    }

    @Test
    @DisplayName("Should handle small files (< 10MB)")
    void testDirectoryLookup_SmallFile() throws Exception {
        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Path smallFile = tempDir.resolve("small.txt");
        Files.write(smallFile, new byte[1024 * 1024]); // 1 MB file

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeastOnce())
                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
        );

        watcherThread.interrupt();
        watcherThread.join(2000);
    }

    @Test
    @DisplayName("Should handle medium files (10MB - 512MB)")
    void testDirectoryLookup_MediumFile() throws Exception {
        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Path mediumFile = tempDir.resolve("medium.txt");
        Files.write(mediumFile, new byte[50 * 1024 * 1024]); // 50 MB file

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeastOnce())
                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
        );

        watcherThread.interrupt();
        watcherThread.join(2000);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when path is not a directory")
    void testDirectoryLookup_InvalidDirectory() throws Exception {
        Path invalidPath = tempDir.resolve("notADirectory.txt");
        Files.createFile(invalidPath);

        Thread watcherThread = new Thread(() -> {
            assertThrows(IllegalArgumentException.class, () ->
                    directoryWatcher.directoryLookup(invalidPath.toString(), "intermediateStore")
            );
        });
        watcherThread.start();
        watcherThread.join(5000);
    }

    @Test
    @DisplayName("Should handle file processing exception gracefully")
    void testDirectoryLookup_ProcessingException() throws Exception {
        doThrow(new RuntimeException("Processing error"))
                .when(fileProcessingService)
                .processFile(any(), anyString(), anyString());

        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Path newFile = tempDir.resolve("errorFile.txt");
        Files.createFile(newFile);

        // Should attempt to process despite exception
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeastOnce())
                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
        );

        watcherThread.interrupt();
        watcherThread.join(2000);
    }

    @Test
    @DisplayName("Should debounce multiple events for same file")
    void testDirectoryLookup_DebounceEvents() throws Exception {
        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Path file = tempDir.resolve("debounce.txt");
        Files.createFile(file);

        // Modify the file multiple times quickly
        for (int i = 0; i < 5; i++) {
            Files.write(file, ("Content " + i).getBytes(), StandardOpenOption.APPEND);
            Thread.sleep(100);
        }

        await().atMost(25, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeastOnce())
                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
        );

        watcherThread.interrupt();
        watcherThread.join(2000);
    }

    @Test
    @DisplayName("Should handle overflow events and rescan directory")
    void testDirectoryLookup_OverflowHandling() throws Exception {
        // Create files before starting watcher to test rescan
        Path existingFile1 = tempDir.resolve("existing1.txt");
        Path existingFile2 = tempDir.resolve("existing2.txt");
        Files.createFile(existingFile1);
        Files.createFile(existingFile2);

        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        // Create new file to trigger processing
        Path newFile = tempDir.resolve("newFile.txt");
        Files.createFile(newFile);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeastOnce())
                        .processFile(any(), anyString(), anyString())
        );

        watcherThread.interrupt();
        watcherThread.join(2000);
    }

    @Test
    @DisplayName("Should handle file deletion during stability check")
    void testDirectoryLookup_FileDeletedDuringCheck() throws Exception {
        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Path file = tempDir.resolve("deleted.txt");
        Files.createFile(file);

        // Delete file shortly after creation (during quiet period)
        Thread.sleep(1000);
        Files.delete(file);

        await().during(5, TimeUnit.SECONDS).until(() -> true);

        watcherThread.interrupt();
        watcherThread.join(2000);
    }

    @Test
    @DisplayName("Should compute dynamic quiet period for tiny files")
    void testComputeDynamicQuietMillis_TinyFile() throws Exception {
        Path tinyFile = tempDir.resolve("tiny.txt");
        Files.write(tinyFile, new byte[1024]); // 1 KB

        // Use reflection to call private method
        Method method = DirectoryWatcher.class.getDeclaredMethod(
                "computeDynamicQuietMillis", Path.class);
        method.setAccessible(true);

        long quietMillis = (long) method.invoke(directoryWatcher, tinyFile);

        assertTrue(quietMillis >= 500 && quietMillis <= 2000,
                "Tiny file should have quiet period between 500ms and 2000ms");
    }

    @Test
    @DisplayName("Should compute dynamic quiet period for medium files")
    void testComputeDynamicQuietMillis_MediumFile() throws Exception {
        Path mediumFile = tempDir.resolve("medium.txt");
        Files.write(mediumFile, new byte[100 * 1024 * 1024]); // 100 MB

        Method method = DirectoryWatcher.class.getDeclaredMethod(
                "computeDynamicQuietMillis", Path.class);
        method.setAccessible(true);

        long quietMillis = (long) method.invoke(directoryWatcher, mediumFile);

        assertTrue(quietMillis >= 2000 && quietMillis <= 10000,
                "Medium file should have quiet period between 2s and 10s");
    }

    @Test
    @DisplayName("Should compute dynamic quiet period for large files")
    void testComputeDynamicQuietMillis_LargeFile() throws Exception {
        // Create a sparse file to simulate large file without using disk space
        Path largeFile = tempDir.resolve("large.txt");
        Files.createFile(largeFile);

        // Simulate large file size using reflection or by creating actual content
        Files.write(largeFile, new byte[512 * 1024 * 1024]); // 512 MB

        Method method = DirectoryWatcher.class.getDeclaredMethod(
                "computeDynamicQuietMillis", Path.class);
        method.setAccessible(true);

        long quietMillis = (long) method.invoke(directoryWatcher, largeFile);

        assertTrue(quietMillis >= 10000,
                "Large file should have quiet period of at least 10s");
    }

    @Test
    @DisplayName("Should handle non-existent file in computeDynamicQuietMillis")
    void testComputeDynamicQuietMillis_NonExistentFile() throws Exception {
        Path nonExistent = tempDir.resolve("doesNotExist.txt");

        Method method = DirectoryWatcher.class.getDeclaredMethod(
                "computeDynamicQuietMillis", Path.class);
        method.setAccessible(true);

        long quietMillis = (long) method.invoke(directoryWatcher, nonExistent);

        assertTrue(quietMillis >= 0, "Should return base quiet period for non-existent file");
    }

    @Test
    @DisplayName("Should handle WatchKey becoming invalid")
    void testDirectoryLookup_InvalidWatchKey() throws Exception {
        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        // Delete the directory to invalidate the watch key
        Thread.sleep(1000);
        FileUtils.deleteDirectory(tempDir.toFile());

        // Thread should terminate gracefully
        await().atMost(10, TimeUnit.SECONDS).until(() -> !watcherThread.isAlive());
    }

    @Test
    @DisplayName("Should process file with special characters in name")
    void testDirectoryLookup_SpecialCharactersInFileName() throws Exception {
        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Path specialFile = tempDir.resolve("file-with-special_chars@123.txt");
        Files.createFile(specialFile);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeastOnce())
                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
        );

        watcherThread.interrupt();
        watcherThread.join(2000);
    }

    @Test
    @DisplayName("Should handle empty file")
    void testDirectoryLookup_EmptyFile() throws Exception {
        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Path emptyFile = tempDir.resolve("empty.txt");
        Files.createFile(emptyFile); // Empty file

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeastOnce())
                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
        );

        watcherThread.interrupt();
        watcherThread.join(2000);
    }

    @Test
    @DisplayName("Should handle concurrent file creations")
    void testDirectoryLookup_ConcurrentFileCreations() throws Exception {
        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        // Create multiple files concurrently
        ExecutorService testExecutor = Executors.newFixedThreadPool(3);
        for (int i = 0; i < 5; i++) {
            final int index = i;
            testExecutor.submit(() -> {
                try {
                    Path file = tempDir.resolve("concurrent_" + index + ".txt");
                    Files.createFile(file);
                } catch (IOException e) {
                    // Ignore
                }
            });
        }

        testExecutor.shutdown();
        testExecutor.awaitTermination(5, TimeUnit.SECONDS);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeast(5))
                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
        );

        watcherThread.interrupt();
        watcherThread.join(2000);
    }

    @Test
    @DisplayName("Should handle file with only extension")
    void testDirectoryLookup_FileWithOnlyExtension() throws Exception {
        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Path dotFile = tempDir.resolve(".txt");
        Files.createFile(dotFile);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeastOnce())
                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
        );

        watcherThread.interrupt();
        watcherThread.join(2000);
    }

    @Test
    @DisplayName("Should handle interrupted exception gracefully")
    void testDirectoryLookup_InterruptedException() throws Exception {
        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        // Interrupt the thread
        watcherThread.interrupt();
        watcherThread.join(5000);

        assertTrue(watcherThread.isInterrupted() || !watcherThread.isAlive(),
                "Thread should handle interruption gracefully");
    }

    @Test
    @DisplayName("Should reschedule when file is not readable")
    void testDirectoryLookup_FileNotReadable() throws Exception {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            // Skip on Windows as file permissions work differently
            return;
        }

        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Path unreadableFile = tempDir.resolve("unreadable.txt");
        Files.createFile(unreadableFile);

        // Make file unreadable temporarily
        unreadableFile.toFile().setReadable(false);

        Thread.sleep(3000);

        // Make it readable again
        unreadableFile.toFile().setReadable(true);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeastOnce())
                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
        );

        watcherThread.interrupt();
        watcherThread.join(2000);
    }

    @Test
    @DisplayName("Should handle rapid file creation and modification")
    void testDirectoryLookup_RapidFileModification() throws Exception {
        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        Path file = tempDir.resolve("rapid.txt");
        Files.createFile(file);

        // Rapidly modify file
        for (int i = 0; i < 10; i++) {
            Files.write(file, ("Line " + i + "\n").getBytes(), StandardOpenOption.APPEND);
            Thread.sleep(50);
        }

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeastOnce())
                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
        );

        watcherThread.interrupt();
        watcherThread.join(2000);
    }

    @Test
    @DisplayName("Should handle files with long names")
    void testDirectoryLookup_LongFileName() throws Exception {
        Thread watcherThread = new Thread(() -> {
            directoryWatcher.directoryLookup(tempDir.toString(), "intermediateStore");
        });
        watcherThread.setDaemon(true);
        watcherThread.start();

        await().atMost(5, TimeUnit.SECONDS).until(() -> Files.exists(tempDir));

        String longName = "a".repeat(200) + ".txt";
        Path longFile = tempDir.resolve(longName);
        Files.createFile(longFile);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeastOnce())
                        .processFile(any(), eq(tempDir.toString()), eq("intermediateStore"))
        );

        watcherThread.interrupt();
        watcherThread.join(2000);
    }

    @Test
    @DisplayName("Should directly test onEvent method with file creation event")
    void testOnEvent_FileCreationEvent() throws Exception {
        Path testFile = tempDir.resolve("onEventTest.txt");
        Files.createFile(testFile);

        // Create a mock WatchEvent
        @SuppressWarnings("unchecked")
        WatchEvent<Path> mockEvent = mock(WatchEvent.class);
        lenient().when(mockEvent.kind()).thenReturn(StandardWatchEventKinds.ENTRY_CREATE);
        lenient().when(mockEvent.context()).thenReturn(testFile.getFileName());

        // Use reflection to call private onEvent method
        Method onEventMethod = DirectoryWatcher.class.getDeclaredMethod(
                "onEvent", Path.class, WatchEvent.class, String.class, String.class);
        onEventMethod.setAccessible(true);

        onEventMethod.invoke(directoryWatcher, testFile, mockEvent, tempDir.toString(), "intermediateStore");

        // Give time for async processing
        Thread.sleep(500);

        // Verify the event was recorded
        Field latestEventsField = DirectoryWatcher.class.getDeclaredField("latestEvents");
        latestEventsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Path, WatchEvent<Path>> latestEvents =
                (ConcurrentHashMap<Path, WatchEvent<Path>>) latestEventsField.get(directoryWatcher);

        assertNotNull(latestEvents.get(testFile));
    }

    @Test
    @DisplayName("Should directly test onEvent method skips directories")
    void testOnEvent_SkipsDirectories() throws Exception {
        Path testDir = tempDir.resolve("testSubDir");
        Files.createDirectory(testDir);

        @SuppressWarnings("unchecked")
        WatchEvent<Path> mockEvent = mock(WatchEvent.class);
        lenient().when(mockEvent.kind()).thenReturn(StandardWatchEventKinds.ENTRY_CREATE);
        lenient().when(mockEvent.context()).thenReturn(testDir.getFileName());

        Method onEventMethod = DirectoryWatcher.class.getDeclaredMethod(
                "onEvent", Path.class, WatchEvent.class, String.class, String.class);
        onEventMethod.setAccessible(true);

        onEventMethod.invoke(directoryWatcher, testDir, mockEvent, tempDir.toString(), "intermediateStore");

        Thread.sleep(500);

        // Verify no scheduled checks were created for directory
        Field scheduledChecksField = DirectoryWatcher.class.getDeclaredField("scheduledChecks");
        scheduledChecksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Path, ScheduledFuture<?>> scheduledChecks =
                (ConcurrentHashMap<Path, ScheduledFuture<?>>) scheduledChecksField.get(directoryWatcher);

        assertNull(scheduledChecks.get(testDir));
    }

    @Test
    @DisplayName("Should directly test onEvent method skips temp files")
    void testOnEvent_SkipsTempFiles() throws Exception {
        Path tempFile = tempDir.resolve("temp.tmp");
        Files.createFile(tempFile);

        @SuppressWarnings("unchecked")
        WatchEvent<Path> mockEvent = mock(WatchEvent.class);
        lenient().when(mockEvent.kind()).thenReturn(StandardWatchEventKinds.ENTRY_CREATE);
        lenient().when(mockEvent.context()).thenReturn(tempFile.getFileName());

        Method onEventMethod = DirectoryWatcher.class.getDeclaredMethod(
                "onEvent", Path.class, WatchEvent.class, String.class, String.class);
        onEventMethod.setAccessible(true);

        onEventMethod.invoke(directoryWatcher, tempFile, mockEvent, tempDir.toString(), "intermediateStore");

        Thread.sleep(500);

        Field scheduledChecksField = DirectoryWatcher.class.getDeclaredField("scheduledChecks");
        scheduledChecksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Path, ScheduledFuture<?>> scheduledChecks =
                (ConcurrentHashMap<Path, ScheduledFuture<?>>) scheduledChecksField.get(directoryWatcher);

        assertNull(scheduledChecks.get(tempFile));
    }

    @Test
    @DisplayName("Should test startStabilityCheck for small file")
    void testStartStabilityCheck_SmallFile() throws Exception {
        Path smallFile = tempDir.resolve("small.txt");
        Files.write(smallFile, new byte[1024 * 1024]); // 1 MB

        Method method = DirectoryWatcher.class.getDeclaredMethod(
                "startStabilityCheck", Path.class, String.class, String.class);
        method.setAccessible(true);

        method.invoke(directoryWatcher, smallFile, tempDir.toString(), "intermediateStore");

        Thread.sleep(1000);

        // Verify probe state was created
        Field probeStatesField = DirectoryWatcher.class.getDeclaredField("probeStates");
        probeStatesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, ?> probeStates = (Map<Path, ?>) probeStatesField.get(directoryWatcher);

        assertNotNull(probeStates.get(smallFile));
    }

    @Test
    @DisplayName("Should test startStabilityCheck for medium file")
    void testStartStabilityCheck_MediumFile() throws Exception {
        Path mediumFile = tempDir.resolve("medium.txt");
        Files.write(mediumFile, new byte[100 * 1024 * 1024]); // 100 MB

        Method method = DirectoryWatcher.class.getDeclaredMethod(
                "startStabilityCheck", Path.class, String.class, String.class);
        method.setAccessible(true);

        method.invoke(directoryWatcher, mediumFile, tempDir.toString(), "intermediateStore");

        Thread.sleep(1000);

        Field probeStatesField = DirectoryWatcher.class.getDeclaredField("probeStates");
        probeStatesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, ?> probeStates = (Map<Path, ?>) probeStatesField.get(directoryWatcher);

        assertNotNull(probeStates.get(mediumFile));
    }

    @Test
    @DisplayName("Should test startStabilityCheck for large file")
    void testStartStabilityCheck_LargeFile() throws Exception {
        Path largeFile = tempDir.resolve("large.txt");
        Files.write(largeFile, new byte[600 * 1024 * 1024]); // 600 MB

        Method method = DirectoryWatcher.class.getDeclaredMethod(
                "startStabilityCheck", Path.class, String.class, String.class);
        method.setAccessible(true);

        method.invoke(directoryWatcher, largeFile, tempDir.toString(), "intermediateStore");

        Thread.sleep(1000);

        Field probeStatesField = DirectoryWatcher.class.getDeclaredField("probeStates");
        probeStatesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, ?> probeStates = (Map<Path, ?>) probeStatesField.get(directoryWatcher);

        assertNotNull(probeStates.get(largeFile));
    }

    @Test
    @DisplayName("Should test startStabilityCheck with non-existent file")
    void testStartStabilityCheck_NonExistentFile() throws Exception {
        Path nonExistent = tempDir.resolve("nonExistent.txt");

        Method method = DirectoryWatcher.class.getDeclaredMethod(
                "startStabilityCheck", Path.class, String.class, String.class);
        method.setAccessible(true);

        method.invoke(directoryWatcher, nonExistent, tempDir.toString(), "intermediateStore");

        Thread.sleep(500);

        // Should not create probe state for non-existent file
        Field probeStatesField = DirectoryWatcher.class.getDeclaredField("probeStates");
        probeStatesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, ?> probeStates = (Map<Path, ?>) probeStatesField.get(directoryWatcher);

        assertNull(probeStates.get(nonExistent));
    }

    @Test
    @DisplayName("Should test runProbe method for stable file")
    void testRunProbe_StableFile() throws Exception {
        Path stableFile = tempDir.resolve("stable.txt");
        Files.write(stableFile, "content".getBytes());

        // Initialize probe state first
        Method startMethod = DirectoryWatcher.class.getDeclaredMethod(
                "startStabilityCheck", Path.class, String.class, String.class);
        startMethod.setAccessible(true);
        startMethod.invoke(directoryWatcher, stableFile, tempDir.toString(), "intermediateStore");

        Thread.sleep(1000);

        // Run probe multiple times to make file stable
        Method probeMethod = DirectoryWatcher.class.getDeclaredMethod(
                "runProbe", Path.class, String.class, String.class);
        probeMethod.setAccessible(true);

        for (int i = 0; i < 5; i++) {
            probeMethod.invoke(directoryWatcher, stableFile, tempDir.toString(), "intermediateStore");
            Thread.sleep(500);
        }

        // File should eventually be processed
        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                verify(fileProcessingService, atLeastOnce())
                        .processFile(any(), anyString(), anyString())
        );
    }
/*
    @Test
    @DisplayName("Should test runProbe method with file modification")
    void testRunProbe_ModifiedFile() throws Exception {
        Path modifiedFile = tempDir.resolve("modified.txt");
        Files.write(modifiedFile, "initial".getBytes());

        Method startMethod = DirectoryWatcher.class.getDeclaredMethod(
                "startStabilityCheck", Path.class, String.class, String.class);
        startMethod.setAccessible(true);
        startMethod.invoke(directoryWatcher, modifiedFile, tempDir.toString(), "intermediateStore");

        Thread.sleep(500);

        Method probeMethod = DirectoryWatcher.class.getDeclaredMethod(
                "runProbe", Path.class, String.class, String.class);
        probeMethod.setAccessible(true);

        // First probe
        probeMethod.invoke(directoryWatcher, modifiedFile, tempDir.toString(), "intermediateStore");
        Thread.sleep(300);

        // Modify file
        Files.write(modifiedFile, "modified content".getBytes());
        Thread.sleep(300);

        // Second probe - should reset stability count
        probeMethod.invoke(directoryWatcher, modifiedFile, tempDir.toString(), "intermediateStore");
        Thread.sleep(300);

        // Verify probe state still exists (not stable yet)
        Field probeStatesField = DirectoryWatcher.class.getDeclaredField("probeStates");
        probeStatesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, ?> probeStates = (Map<Path, ?>) probeStatesField.get(directoryWatcher);

        assertNotNull(probeStates.get(modifiedFile));
    }*/

    @Test
    @DisplayName("Should test runProbe with missing probe state")
    void testRunProbe_MissingProbeState() throws Exception {
        Path file = tempDir.resolve("noprobe.txt");
        Files.write(file, "content".getBytes());

        // Call runProbe without initializing probe state
        Method probeMethod = DirectoryWatcher.class.getDeclaredMethod(
                "runProbe", Path.class, String.class, String.class);
        probeMethod.setAccessible(true);

        probeMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");

        Thread.sleep(500);

        // Should initialize probe state
        Field probeStatesField = DirectoryWatcher.class.getDeclaredField("probeStates");
        probeStatesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, ?> probeStates = (Map<Path, ?>) probeStatesField.get(directoryWatcher);

        assertNotNull(probeStates.get(file));
    }

    @Test
    @DisplayName("Should test reschedule method")
    void testReschedule_Method() throws Exception {
        Path file = tempDir.resolve("reschedule.txt");
        Files.write(file, "content".getBytes());

        Method rescheduleMethod = DirectoryWatcher.class.getDeclaredMethod(
                "reschedule", Path.class, String.class, String.class);
        rescheduleMethod.setAccessible(true);

        rescheduleMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");

        Thread.sleep(500);

        // Verify retry count was incremented
        Field retryCountsField = DirectoryWatcher.class.getDeclaredField("retryCounts");
        retryCountsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, Integer> retryCounts = (Map<Path, Integer>) retryCountsField.get(directoryWatcher);

        assertEquals(1, retryCounts.get(file));

        // Reschedule again
        rescheduleMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");
        Thread.sleep(500);

        assertEquals(2, retryCounts.get(file));
    }


    @Test
    @DisplayName("Should test cleanup method")
    @Disabled("Ignoring cleanup test temporarily")
    void testCleanup_Method() throws Exception {
        Path file = tempDir.resolve("cleanup.txt");
        Files.write(file, "content".getBytes());

        // First create some state
        Method startMethod = DirectoryWatcher.class.getDeclaredMethod(
                "startStabilityCheck", Path.class, String.class, String.class);
        startMethod.setAccessible(true);
        startMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");

        Thread.sleep(500);

        // Verify state exists
        Field probeStatesField = DirectoryWatcher.class.getDeclaredField("probeStates");
        probeStatesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, ?> probeStates = (Map<Path, ?>) probeStatesField.get(directoryWatcher);
        assertNotNull(probeStates.get(file));

        // Call cleanup
        Method cleanupMethod = DirectoryWatcher.class.getDeclaredMethod(
                "cleanup", Path.class);
        cleanupMethod.setAccessible(true);
        cleanupMethod.invoke(directoryWatcher, file);

        // Verify state was cleaned up
        assertNull(probeStates.get(file));

        Field scheduledChecksField = DirectoryWatcher.class.getDeclaredField("scheduledChecks");
        scheduledChecksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Path, ScheduledFuture<?>> scheduledChecks =
                (ConcurrentHashMap<Path, ScheduledFuture<?>>) scheduledChecksField.get(directoryWatcher);
        assertNull(scheduledChecks.get(file));
    }

    @Test
    @DisplayName("Should test rescanDirectory method")
    void testRescanDirectory_Method() throws Exception {
        // Create some files before rescan
        Path file1 = tempDir.resolve("rescan1.txt");
        Path file2 = tempDir.resolve("rescan2.txt");
        Files.write(file1, "content1".getBytes());
        Files.write(file2, "content2".getBytes());

        Method rescanMethod = DirectoryWatcher.class.getDeclaredMethod(
                "rescanDirectory", Path.class, String.class, String.class);
        rescanMethod.setAccessible(true);

        rescanMethod.invoke(directoryWatcher, tempDir, tempDir.toString(), "intermediateStore");

        Thread.sleep(1000);

        // Verify events were created for existing files
        Field latestEventsField = DirectoryWatcher.class.getDeclaredField("latestEvents");
        latestEventsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Path, WatchEvent<Path>> latestEvents =
                (ConcurrentHashMap<Path, WatchEvent<Path>>) latestEventsField.get(directoryWatcher);

        assertTrue(latestEvents.containsKey(file1) || latestEvents.containsKey(file2));
    }

    @Test
    @DisplayName("Should test SyntheticWatchEvent creation")
    void testSyntheticWatchEvent_Creation() throws Exception {
        // Access inner class through reflection
        Class<?> syntheticEventClass = Class.forName(
                "com.eh.digitalpathology.dicomreceiver.service.DirectoryWatcher$SyntheticWatchEvent");

        Method createMethod = syntheticEventClass.getDeclaredMethod(
                "create", WatchEvent.Kind.class, Path.class);
        createMethod.setAccessible(true);

        Path testPath = tempDir.resolve("test.txt");
        Object syntheticEvent = createMethod.invoke(null, StandardWatchEventKinds.ENTRY_CREATE, testPath);

        assertNotNull(syntheticEvent);

        // Verify kind method
        Method kindMethod = syntheticEventClass.getDeclaredMethod("kind");
        kindMethod.setAccessible(true);
        Object kind = kindMethod.invoke(syntheticEvent);
        assertEquals(StandardWatchEventKinds.ENTRY_CREATE, kind);

        // Verify context method
        Method contextMethod = syntheticEventClass.getDeclaredMethod("context");
        contextMethod.setAccessible(true);
        Object context = contextMethod.invoke(syntheticEvent);
        assertEquals(testPath, context);

        // Verify count method
        Method countMethod = syntheticEventClass.getDeclaredMethod("count");
        countMethod.setAccessible(true);
        Object count = countMethod.invoke(syntheticEvent);
        assertEquals(1, count);
    }

    @Test
    @DisplayName("Should test exponential backoff in reschedule")
    void testReschedule_ExponentialBackoff() throws Exception {
        Path file = tempDir.resolve("backoff.txt");
        Files.write(file, "content".getBytes());

        Method rescheduleMethod = DirectoryWatcher.class.getDeclaredMethod(
                "reschedule", Path.class, String.class, String.class);
        rescheduleMethod.setAccessible(true);

        Field retryCountsField = DirectoryWatcher.class.getDeclaredField("retryCounts");
        retryCountsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, Integer> retryCounts = (Map<Path, Integer>) retryCountsField.get(directoryWatcher);

        // First reschedule
        rescheduleMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");
        assertEquals(1, retryCounts.get(file));

        // Second reschedule
        rescheduleMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");
        assertEquals(2, retryCounts.get(file));

        // Third reschedule
        rescheduleMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");
        assertEquals(3, retryCounts.get(file));

        // Fourth reschedule
        rescheduleMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");
        assertEquals(4, retryCounts.get(file));
    }

   /* @Test
    @DisplayName("Should test ProbeState class")
    void testProbeState_Class() throws Exception {
        Class<?> probeStateClass = Class.forName(
                "com.eh.digitalpathology.dicomreceiver.service.DirectoryWatcher$ProbeState");

        Object probeState = probeStateClass.getDeclaredConstructor().newInstance();
        assertNotNull(probeState);

        // Test default values
        Field prevSizeField = probeStateClass.getDeclaredField("prevSize");
        prevSizeField.setAccessible(true);
        assertEquals(-1L, prevSizeField.get(probeState));

        Field stableCountField = probeStateClass.getDeclaredField("stableCount");
        stableCountField.setAccessible(true);
        assertEquals(0, stableCountField.get(probeState));

        // Set values
        Field probesRequiredField = probeStateClass.getDeclaredField("probesRequired");
        probesRequiredField.setAccessible(true);
        probesRequiredField.set(probeState, 3);
        assertEquals(3, probesRequiredField.get(probeState));

        Field probeMsField = probeStateClass.getDeclaredField("probeMs");
        probeMsField.setAccessible(true);
        probeMsField.set(probeState, 1500L);
        assertEquals(1500L, probeMsField.get(probeState));
    }
*/
    @Test
    @DisplayName("Should handle IOException in rescanDirectory")
    void testRescanDirectory_IOException() throws Exception {
        Path nonExistentDir = tempDir.resolve("nonExistent");

        Method rescanMethod = DirectoryWatcher.class.getDeclaredMethod(
                "rescanDirectory", Path.class, String.class, String.class);
        rescanMethod.setAccessible(true);

        // Should handle IOException gracefully
        assertDoesNotThrow(() ->
                rescanMethod.invoke(directoryWatcher, nonExistentDir, tempDir.toString(), "intermediateStore")
        );
    }

    @Test
    @DisplayName("Should test debouncing cancels previous scheduled check")
    void testOnEvent_DebouncingCancelsPreviousCheck() throws Exception {
        Path file = tempDir.resolve("debounce.txt");
        Files.createFile(file);

        @SuppressWarnings("unchecked")
        WatchEvent<Path> mockEvent = mock(WatchEvent.class);
       lenient().when(mockEvent.kind()).thenReturn(StandardWatchEventKinds.ENTRY_CREATE);
        lenient().when(mockEvent.context()).thenReturn(file.getFileName());

        Method onEventMethod = DirectoryWatcher.class.getDeclaredMethod(
                "onEvent", Path.class, WatchEvent.class, String.class, String.class);
        onEventMethod.setAccessible(true);

        // First event
        onEventMethod.invoke(directoryWatcher, file, mockEvent, tempDir.toString(), "intermediateStore");
        Thread.sleep(200);

        // Second event (should cancel first)
        onEventMethod.invoke(directoryWatcher, file, mockEvent, tempDir.toString(), "intermediateStore");
        Thread.sleep(200);

        // Third event
        onEventMethod.invoke(directoryWatcher, file, mockEvent, tempDir.toString(), "intermediateStore");
        Thread.sleep(200);

        // Verify only one scheduled check exists
        Field scheduledChecksField = DirectoryWatcher.class.getDeclaredField("scheduledChecks");
        scheduledChecksField.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<Path, ScheduledFuture<?>> scheduledChecks =
                (ConcurrentHashMap<Path, ScheduledFuture<?>>) scheduledChecksField.get(directoryWatcher);

        assertEquals(1, scheduledChecks.size());
    }

    @Test
    @DisplayName("Should handle very large file size calculations")
    void testComputeDynamicQuietMillis_VeryLargeFile() throws Exception {
        Path largeFile = tempDir.resolve("verylarge.txt");
        Files.createFile(largeFile);

        // Create 2GB file simulation
        Files.write(largeFile, new byte[Integer.MAX_VALUE / 2]);

        Method method = DirectoryWatcher.class.getDeclaredMethod(
                "computeDynamicQuietMillis", Path.class);
        method.setAccessible(true);

        long quietMillis = (long) method.invoke(directoryWatcher, largeFile);

        // Should be capped at 240 seconds (240000 ms)
        assertTrue(quietMillis <= 240000,
                "Very large file should be capped at max quiet period");
    }

    @Test
    @DisplayName("Should test runProbe with deleted file")
    void testRunProbe_DeletedFile() throws Exception {
        Path file = tempDir.resolve("deleted.txt");
        Files.write(file, "content".getBytes());

        // Initialize probe state
        Method startMethod = DirectoryWatcher.class.getDeclaredMethod(
                "startStabilityCheck", Path.class, String.class, String.class);
        startMethod.setAccessible(true);
        startMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");

        Thread.sleep(500);

        // Delete the file
        Files.delete(file);

        // Run probe
        Method probeMethod = DirectoryWatcher.class.getDeclaredMethod(
                "runProbe", Path.class, String.class, String.class);
        probeMethod.setAccessible(true);
        probeMethod.invoke(directoryWatcher, file, tempDir.toString(), "intermediateStore");

        Thread.sleep(500);

        // Verify probe state was removed
        Field probeStatesField = DirectoryWatcher.class.getDeclaredField("probeStates");
        probeStatesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Path, ?> probeStates = (Map<Path, ?>) probeStatesField.get(directoryWatcher);

        assertNull(probeStates.get(file));
    }
}
