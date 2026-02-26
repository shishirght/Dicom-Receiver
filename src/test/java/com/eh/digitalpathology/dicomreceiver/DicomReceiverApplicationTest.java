package com.eh.digitalpathology.dicomreceiver;

import com.eh.digitalpathology.dicomreceiver.service.DirectoryWatcher;
import com.eh.digitalpathology.dicomreceiver.service.RemoteDirectoryWatcher;
import com.eh.digitalpathology.dicomreceiver.util.CommonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 tests for DicomReceiverApplication without starting a Spring context.
 */
class DicomReceiverApplicationTest {

    /**
     * Executor that runs tasks synchronously on the calling thread.
     * Makes CommandLineRunner tasks deterministic in tests.
     */
    static class DirectExecutorService extends AbstractExecutorService {
        private volatile boolean shutdown;

        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow() { shutdown = true; return List.of(); }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public void execute(Runnable command) { command.run(); }
    }

    /** Swap the app's private executor with a deterministic one. */
    private static void setExecutor(DicomReceiverApplication app, ExecutorService executor) throws Exception {
        Field f = DicomReceiverApplication.class.getDeclaredField("executorService");
        f.setAccessible(true);
        f.set(app, executor);
    }

    @Test
    @DisplayName("run(): remote watcher ENABLED -> local and remote tasks are invoked")
    void run_remoteEnabled_invokesLocalAndRemote() throws Exception {
        // Arrange
        DirectoryWatcher dirWatcher = mock(DirectoryWatcher.class);
        RemoteDirectoryWatcher remoteWatcher = mock(RemoteDirectoryWatcher.class);
        CommonUtils utils = mock(CommonUtils.class);

        when(utils.getLocalStoragePath()).thenReturn("/data/received");
        when(utils.getIntermediateFileServer()).thenReturn("/data/intermediate");
        when(utils.isEnableRemoteDirectoryWatcher()).thenReturn(true);

        DicomReceiverApplication app = new DicomReceiverApplication(dirWatcher, utils, remoteWatcher);
        setExecutor(app, new DirectExecutorService());

        // Act
        app.run();

        // Assert
        verify(dirWatcher).directoryLookup("/data/received", "/data/intermediate");
        verify(remoteWatcher).watchSharedDirectory();
        verifyNoMoreInteractions(dirWatcher, remoteWatcher);
    }

    @Test
    @DisplayName("run(): remote watcher DISABLED -> only local task is invoked")
    void run_remoteDisabled_invokesOnlyLocal() throws Exception {
        // Arrange
        DirectoryWatcher dirWatcher = mock(DirectoryWatcher.class);
        RemoteDirectoryWatcher remoteWatcher = mock(RemoteDirectoryWatcher.class);
        CommonUtils utils = mock(CommonUtils.class);

        when(utils.getLocalStoragePath()).thenReturn("/mnt/received");
        when(utils.getIntermediateFileServer()).thenReturn("/mnt/intermediate");
        when(utils.isEnableRemoteDirectoryWatcher()).thenReturn(false);

        DicomReceiverApplication app = new DicomReceiverApplication(dirWatcher, utils, remoteWatcher);
        setExecutor(app, new DirectExecutorService());

        // Act
        app.run();

        // Assert
        verify(dirWatcher).directoryLookup("/mnt/received", "/mnt/intermediate");
        verify(remoteWatcher, never()).watchSharedDirectory();
        verifyNoMoreInteractions(dirWatcher, remoteWatcher);
    }

    @Test
    @DisplayName("run(): remote watcher ENABLED but throws -> error handled, no rethrow")
    void run_remoteEnabled_remoteThrows_exceptionHandled() throws Exception {
        // Arrange
        DirectoryWatcher dirWatcher = mock(DirectoryWatcher.class);
        RemoteDirectoryWatcher remoteWatcher = mock(RemoteDirectoryWatcher.class);
        CommonUtils utils = mock(CommonUtils.class);

        when(utils.getLocalStoragePath()).thenReturn("/r/received");
        when(utils.getIntermediateFileServer()).thenReturn("/r/intermediate");
        when(utils.isEnableRemoteDirectoryWatcher()).thenReturn(true);

        doThrow(new RuntimeException("boom")).when(remoteWatcher).watchSharedDirectory();

        DicomReceiverApplication app = new DicomReceiverApplication(dirWatcher, utils, remoteWatcher);
        setExecutor(app, new DirectExecutorService());

        // Act & Assert: run() should not propagate the exception
        assertDoesNotThrow(() -> {
            try {
                app.run();
            } catch (Exception e) {
                throw new AssertionError("run() should not rethrow remote watcher exceptions", e);
            }
        });

        verify(dirWatcher).directoryLookup("/r/received", "/r/intermediate");
        verify(remoteWatcher).watchSharedDirectory();
        verifyNoMoreInteractions(dirWatcher, remoteWatcher);
    }

    @Test
    @DisplayName("shutdown(): when awaitTermination=false -> shutdownNow and second await")
    void shutdown_forcesTerminationWhenNotTerminated() throws Exception {
        // Arrange
        DirectoryWatcher dirWatcher = mock(DirectoryWatcher.class);
        RemoteDirectoryWatcher remoteWatcher = mock(RemoteDirectoryWatcher.class);
        CommonUtils utils = mock(CommonUtils.class);

        DicomReceiverApplication app = new DicomReceiverApplication(dirWatcher, utils, remoteWatcher);

        ExecutorService exec = mock(ExecutorService.class);
        when(exec.awaitTermination(60, TimeUnit.SECONDS))
                .thenReturn(false)  // after shutdown()
                .thenReturn(false); // after shutdownNow()

        setExecutor(app, exec);

        // Act
        assertDoesNotThrow(app::shutdown);

        // Assert sequence
        InOrder order = inOrder(exec);
        order.verify(exec).shutdown();
        order.verify(exec).awaitTermination(60, TimeUnit.SECONDS);
        order.verify(exec).shutdownNow();
        order.verify(exec).awaitTermination(60, TimeUnit.SECONDS);
        verifyNoMoreInteractions(exec);
    }

    @Test
    @DisplayName("shutdown(): when awaitTermination throws InterruptedException -> shutdownNow and interrupt restored")
    void shutdown_handlesInterruptedException() throws Exception {
        // Arrange
        DirectoryWatcher dirWatcher = mock(DirectoryWatcher.class);
        RemoteDirectoryWatcher remoteWatcher = mock(RemoteDirectoryWatcher.class);
        CommonUtils utils = mock(CommonUtils.class);

        DicomReceiverApplication app = new DicomReceiverApplication(dirWatcher, utils, remoteWatcher);

        ExecutorService exec = mock(ExecutorService.class);
        when(exec.awaitTermination(60, TimeUnit.SECONDS))
                .thenThrow(new InterruptedException("test interrupt"));

        setExecutor(app, exec);

        boolean wasInterrupted = Thread.currentThread().isInterrupted();

        // Act
        assertDoesNotThrow(app::shutdown);

        // Assert calls
        verify(exec).shutdown();
        verify(exec).awaitTermination(60, TimeUnit.SECONDS);
        verify(exec).shutdownNow();
        verifyNoMoreInteractions(exec);

        // Clean up interrupt flag for other tests in same JVM (best effort).
        if (!wasInterrupted && Thread.currentThread().isInterrupted()) {
            Thread.interrupted(); // clears interrupt
        }
    }
}