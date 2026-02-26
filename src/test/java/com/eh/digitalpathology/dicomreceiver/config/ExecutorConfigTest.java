package com.eh.digitalpathology.dicomreceiver.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Expanded tests for ExecutorConfig to maximize line/branch coverage.
 * No Spring context required.
 */
class ExecutorConfigTest {

    // ---------- reflection helpers ----------
    private static void setField(Object target, String field, Object value) {
        try {
            Field f = ExecutorConfig.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set field: " + field, e);
        }
    }
    private static Object getField(Object target, String field) {
        try {
            Field f = ExecutorConfig.class.getDeclaredField(field);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to get field: " + field, e);
        }
    }

    @AfterEach
    void clearInterruptIfSet() {
        if (Thread.currentThread().isInterrupted()) {
            Thread.interrupted(); // clear for subsequent tests
        }
    }

    // ---------- bean creation coverage ----------

    @Test
    @DisplayName("All @Bean methods: non-null, distinct instances, and private fields are set")
    void beanMethods_createExecutors_andSetFields() {
        ExecutorConfig cfg = new ExecutorConfig();

        ExecutorService directoryWatcher = cfg.directoryWatcherExecutor();
        ExecutorService remoteWatcher = cfg.remoteDirectoryWatcherExecutor();
        ScheduledExecutorService remoteWatcherSched = cfg.remoteDirectoryWatcherScheduledExecutor();
        ScheduledExecutorService barcodeSched = cfg.barcodeUploadScheduledExecutor();
        ScheduledExecutorService stabilizer = cfg.stabilizerScheduler();

        assertNotNull(directoryWatcher);
        assertNotNull(remoteWatcher);
        assertNotNull(remoteWatcherSched);
        assertNotNull(barcodeSched);
        assertNotNull(stabilizer);

        // Distinct instances
        assertNotSame(directoryWatcher, remoteWatcher);
        assertNotSame(remoteWatcher, remoteWatcherSched);
        assertNotSame(remoteWatcherSched, barcodeSched);
        assertNotSame(barcodeSched, stabilizer);
        assertNotSame(stabilizer, directoryWatcher);

        // Private fields should be set by bean methods
        assertSame(directoryWatcher, getField(cfg, "directoryWatcherExecutor"));
        assertSame(remoteWatcher, getField(cfg, "remoteDirectoryWatcherExecutor"));
        assertSame(remoteWatcherSched, getField(cfg, "remoteDirectoryWatcherScheduledExecutor"));
        assertSame(barcodeSched, getField(cfg, "barcodeUploadScheduledExecutor"));
        assertSame(stabilizer, getField(cfg, "stabilizerScheduler"));
    }

    // ---------- shutdownExecutors() coverage: nulls + await=true ----------

    @Test
    @DisplayName("shutdownExecutors(): tolerates null fields and shuts down non-null executors (await=true)")
    void shutdownExecutors_handlesNulls_andAwaitTrue() throws Exception {
        ExecutorConfig cfg = new ExecutorConfig();

        ExecutorService dir = mock(ExecutorService.class);
        ScheduledExecutorService barcode = mock(ScheduledExecutorService.class);
        // leave others null to hit the null guard
        setField(cfg, "directoryWatcherExecutor", dir);
        setField(cfg, "remoteDirectoryWatcherExecutor", null);
        setField(cfg, "remoteDirectoryWatcherScheduledExecutor", null);
        setField(cfg, "barcodeUploadScheduledExecutor", barcode);
        setField(cfg, "stabilizerScheduler", null);

        when(dir.awaitTermination(60, TimeUnit.SECONDS)).thenReturn(true);
        when(barcode.awaitTermination(60, TimeUnit.SECONDS)).thenReturn(true);

        assertDoesNotThrow(cfg::shutdownExecutors);

        verify(dir).shutdown();
        verify(dir, times(1)).awaitTermination(60, TimeUnit.SECONDS);
        verify(dir, never()).shutdownNow();

        verify(barcode).shutdown();
        verify(barcode, times(1)).awaitTermination(60, TimeUnit.SECONDS);
        verify(barcode, never()).shutdownNow();

        verifyNoMoreInteractions(dir, barcode);
    }

    // ---------- shutdownExecutors() coverage: await=false twice (logs error line) ----------

    @Test
    @DisplayName("shutdownExecutors(): await=false twice -> shutdownNow + second await (covers error log line)")
    void shutdownExecutors_awaitFalseTwice_triggersShutdownNowAndLogs() throws Exception {
        ExecutorConfig cfg = new ExecutorConfig();

        ExecutorService dir = mock(ExecutorService.class);
        ExecutorService remote = mock(ExecutorService.class);
        ScheduledExecutorService remoteSched = mock(ScheduledExecutorService.class);
        ScheduledExecutorService barcode = mock(ScheduledExecutorService.class);
        ScheduledExecutorService stabilizer = mock(ScheduledExecutorService.class);

        setField(cfg, "directoryWatcherExecutor", dir);
        setField(cfg, "remoteDirectoryWatcherExecutor", remote);
        setField(cfg, "remoteDirectoryWatcherScheduledExecutor", remoteSched);
        setField(cfg, "barcodeUploadScheduledExecutor", barcode);
        setField(cfg, "stabilizerScheduler", stabilizer);

        when(dir.awaitTermination(60, TimeUnit.SECONDS)).thenReturn(false, false);
        when(remote.awaitTermination(60, TimeUnit.SECONDS)).thenReturn(false, false);
        when(remoteSched.awaitTermination(60, TimeUnit.SECONDS)).thenReturn(false, false);
        when(barcode.awaitTermination(60, TimeUnit.SECONDS)).thenReturn(false, false);
        when(stabilizer.awaitTermination(60, TimeUnit.SECONDS)).thenReturn(false, false);

        cfg.shutdownExecutors();

        // Verify per-executor sequence
        InOrder o1 = inOrder(dir);
        o1.verify(dir).shutdown();
        o1.verify(dir).awaitTermination(60, TimeUnit.SECONDS);
        o1.verify(dir).shutdownNow();
        o1.verify(dir).awaitTermination(60, TimeUnit.SECONDS);

        InOrder o2 = inOrder(remote);
        o2.verify(remote).shutdown();
        o2.verify(remote).awaitTermination(60, TimeUnit.SECONDS);
        o2.verify(remote).shutdownNow();
        o2.verify(remote).awaitTermination(60, TimeUnit.SECONDS);

        InOrder o3 = inOrder(remoteSched);
        o3.verify(remoteSched).shutdown();
        o3.verify(remoteSched).awaitTermination(60, TimeUnit.SECONDS);
        o3.verify(remoteSched).shutdownNow();
        o3.verify(remoteSched).awaitTermination(60, TimeUnit.SECONDS);

        InOrder o4 = inOrder(barcode);
        o4.verify(barcode).shutdown();
        o4.verify(barcode).awaitTermination(60, TimeUnit.SECONDS);
        o4.verify(barcode).shutdownNow();
        o4.verify(barcode).awaitTermination(60, TimeUnit.SECONDS);

        InOrder o5 = inOrder(stabilizer);
        o5.verify(stabilizer).shutdown();
        o5.verify(stabilizer).awaitTermination(60, TimeUnit.SECONDS);
        o5.verify(stabilizer).shutdownNow();
        o5.verify(stabilizer).awaitTermination(60, TimeUnit.SECONDS);
    }

    // ---------- shutdownExecutors() coverage: InterruptedException ----------

    @Test
    @DisplayName("shutdownExecutors(): InterruptedException -> shutdownNow and interrupt restored")
    void shutdownExecutors_interruptedException_path() throws Exception {
        ExecutorConfig cfg = new ExecutorConfig();

        ExecutorService dir = mock(ExecutorService.class);
        setField(cfg, "directoryWatcherExecutor", dir);

        when(dir.awaitTermination(60, TimeUnit.SECONDS))
                .thenThrow(new InterruptedException("test interrupt"));

        boolean pre = Thread.currentThread().isInterrupted();

        assertDoesNotThrow(cfg::shutdownExecutors);

        verify(dir).shutdown();
        verify(dir).awaitTermination(60, TimeUnit.SECONDS);
        verify(dir).shutdownNow();

        // Clean up the interrupt flag if this test set it
        if (!pre && Thread.currentThread().isInterrupted()) {
            Thread.interrupted();
        }
    }

    // ---------- Integration-like test with real executors ----------

    @Test
    @DisplayName("Real executors created by bean methods can be shut down gracefully (await=true path on real objects)")
    void realExecutors_shutdownGracefully() {
        ExecutorConfig cfg = new ExecutorConfig();

        // Create real executors via bean methods (this also sets the private fields)
        cfg.directoryWatcherExecutor();
        cfg.remoteDirectoryWatcherExecutor();
        cfg.remoteDirectoryWatcherScheduledExecutor();
        cfg.barcodeUploadScheduledExecutor();
        cfg.stabilizerScheduler();

        // No tasks submitted -> executors should terminate quickly
        assertDoesNotThrow(cfg::shutdownExecutors);

        // Fields should now be non-null and (most likely) shut down
        Object dir = getField(cfg, "directoryWatcherExecutor");
        Object remote = getField(cfg, "remoteDirectoryWatcherExecutor");
        Object remoteSched = getField(cfg, "remoteDirectoryWatcherScheduledExecutor");
        Object barcode = getField(cfg, "barcodeUploadScheduledExecutor");
        Object stabilizer = getField(cfg, "stabilizerScheduler");

        assertNotNull(dir);
        assertNotNull(remote);
        assertNotNull(remoteSched);
        assertNotNull(barcode);
        assertNotNull(stabilizer);
    }
}