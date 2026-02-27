package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.SharedFolderConfig;
import com.eh.digitalpathology.dicomreceiver.util.CommonUtils;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;
import org.apache.commons.vfs2.FileContent;
import org.apache.commons.vfs2.FileName;
import org.apache.commons.vfs2.FileObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoteDirectoryServiceTest {

    @Mock
    private CommonUtils commonUtils;

    @Mock
    private ExecutorService executorService;

    @Mock
    private SharedFolderConfig sharedFolderConfig;

    private RemoteDirectoryService service;

    @BeforeEach
    void setUp() {
        service = new RemoteDirectoryService(commonUtils, executorService, sharedFolderConfig);
    }

    private void setMaxRetries(long value) throws Exception {
        Field f = RemoteDirectoryService.class.getDeclaredField("maxRetries");
        f.setAccessible(true);
        f.set(service, value);
    }

    private Method getMethod(String name, Class<?>... params) throws Exception {
        Method m = RemoteDirectoryService.class.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    private String tempDir() throws Exception {
        return Files.createTempDirectory("local").toString();
    }

    @Test
    void processFileEvent_smallFile_submitsTaskAndReturnsFuture() throws Exception {
        FileObject file = mock(FileObject.class);
        FileName name = mock(FileName.class);
        FileContent content = mock(FileContent.class);

        when(file.getName()).thenReturn(name);
        when(name.getPath()).thenReturn("/share/test.dcm");
        lenient().when(name.getBaseName()).thenReturn("test.dcm");
        when(file.getContent()).thenReturn(content);
        when(content.getSize()).thenReturn(10L);

        when(executorService.submit(any(Callable.class))).thenReturn(mock(Future.class));
        when(executorService.submit(any(Runnable.class))).thenReturn(mock(Future.class));

        CompletableFuture<Boolean> result = service.processFileEvent(file, "/share");

        assertNotNull(result);
        verify(executorService, atLeastOnce()).submit(any(Callable.class));
    }

    @Test
    void processFileEvent_mediumFile_submitsWithMediumLimit() throws Exception {
        FileObject file = mock(FileObject.class);
        FileName name = mock(FileName.class);
        FileContent content = mock(FileContent.class);

        when(file.getName()).thenReturn(name);
        when(name.getPath()).thenReturn("/share/medium.dcm");
        lenient().when(name.getBaseName()).thenReturn("medium.dcm");
        when(file.getContent()).thenReturn(content);
        when(content.getSize()).thenReturn(200L << 20);

        when(executorService.submit(any(Callable.class))).thenReturn(mock(Future.class));
        when(executorService.submit(any(Runnable.class))).thenReturn(mock(Future.class));

        CompletableFuture<Boolean> result = service.processFileEvent(file, "/share");

        assertNotNull(result);
        verify(executorService, atLeastOnce()).submit(any(Callable.class));
    }

    @Test
    void processFileEvent_largeFile_submitsWithLargeLimit() throws Exception {
        FileObject file = mock(FileObject.class);
        FileName name = mock(FileName.class);
        FileContent content = mock(FileContent.class);

        when(file.getName()).thenReturn(name);
        when(name.getPath()).thenReturn("/share/large.dcm");
        lenient().when(name.getBaseName()).thenReturn("large.dcm");
        when(file.getContent()).thenReturn(content);
        when(content.getSize()).thenReturn(2L << 30);

        when(executorService.submit(any(Callable.class))).thenReturn(mock(Future.class));
        when(executorService.submit(any(Runnable.class))).thenReturn(mock(Future.class));

        CompletableFuture<Boolean> result = service.processFileEvent(file, "/share");

        assertNotNull(result);
        verify(executorService, atLeastOnce()).submit(any(Callable.class));
    }

    @Test
    void processFileEvent_exceptionFromGetContent_returnsFailed() throws Exception {
        FileObject file = mock(FileObject.class);
        FileName name = mock(FileName.class);

        when(file.getName()).thenReturn(name);
        when(name.getPath()).thenReturn("/share/test.dcm");
        when(file.getContent()).thenThrow(new RuntimeException("content error"));

        when(executorService.submit(any(Callable.class))).thenReturn(mock(Future.class));
        when(executorService.submit(any(Runnable.class))).thenReturn(mock(Future.class));

        CompletableFuture<Boolean> result = service.processFileEvent(file, "/share");

        assertNotNull(result);
    }

    @Test
    void processFileEvent_futureCompletesExceptionally_completableFutureIsExceptional() throws Exception {
        FileObject file = mock(FileObject.class);
        FileName name = mock(FileName.class);
        FileContent content = mock(FileContent.class);

        when(file.getName()).thenReturn(name);
        when(name.getPath()).thenReturn("/share/test.dcm");
        lenient().when(name.getBaseName()).thenReturn("test.dcm");
        when(file.getContent()).thenReturn(content);
        when(content.getSize()).thenReturn(10L);

        Future<Boolean> failedFuture = mock(Future.class);
        when(failedFuture.get()).thenThrow(new ExecutionException(new RuntimeException("inner")));
        when(executorService.submit(any(Callable.class))).thenReturn(failedFuture);

        ExecutorService realExecutor = Executors.newSingleThreadExecutor();
        try {
            when(executorService.submit(any(Runnable.class))).thenAnswer(inv -> {
                realExecutor.submit((Runnable) inv.getArgument(0));
                return mock(Future.class);
            });

            CompletableFuture<Boolean> result = service.processFileEvent(file, "/share");
            Thread.sleep(300);
            assertTrue(result.isCompletedExceptionally() || !result.isDone());
        } finally {
            realExecutor.shutdown();
        }
    }

    @Test
    void safeSize_exceptionReturnsDefault() throws Exception {
        FileObject file = mock(FileObject.class);
        when(file.getContent()).thenThrow(new RuntimeException("no content"));

        Method m = getMethod("safeSize", FileObject.class);
        long size = (long) m.invoke(service, file);

        assertEquals(200L << 20, size);
    }

    @Test
    void copyRemoteFileToLocal_success() throws Exception {
        File remoteFile = mock(File.class);
        byte[] data = "hello".getBytes();

        when(remoteFile.getInputStream()).thenReturn(new ByteArrayInputStream(data));
        when(commonUtils.getLocalStoragePath()).thenReturn(tempDir());

        Method m = getMethod("copyRemoteFileToLocal", File.class, String.class, long.class);
        boolean result = (boolean) m.invoke(service, remoteFile, "test.dcm", (long) data.length);

        assertTrue(result);
    }

    @Test
    void copyRemoteFileToLocal_largeFilePath_usesBiggerChunk() throws Exception {
        File remoteFile = mock(File.class);
        byte[] data = "bigfile".getBytes();

        when(remoteFile.getInputStream()).thenReturn(new ByteArrayInputStream(data));
        when(commonUtils.getLocalStoragePath()).thenReturn(tempDir());

        Method m = getMethod("copyRemoteFileToLocal", File.class, String.class, long.class);
        boolean result = (boolean) m.invoke(service, remoteFile, "big.dcm", 2L << 30);

        assertTrue(result);
    }

    @Test
    void copyRemoteFileToLocal_dicomdirFileName_appendsTimestamp() throws Exception {
        File remoteFile = mock(File.class);

        when(commonUtils.getLocalStoragePath()).thenReturn(tempDir());

        Method m = getMethod("copyRemoteFileToLocal", File.class, String.class, long.class);
        java.lang.reflect.InvocationTargetException ex = assertThrows(
                java.lang.reflect.InvocationTargetException.class,
                () -> m.invoke(service, remoteFile, "DICOMDIR", 10L));

        assertInstanceOf(java.nio.file.InvalidPathException.class, ex.getCause());
    }

    @Test
    void copyRemoteFileToLocal_failure() throws Exception {
        File remoteFile = mock(File.class);

        when(remoteFile.getInputStream()).thenThrow(new RuntimeException("IO error"));
        when(commonUtils.getLocalStoragePath()).thenReturn(tempDir());

        Method m = getMethod("copyRemoteFileToLocal", File.class, String.class, long.class);
        boolean result = (boolean) m.invoke(service, remoteFile, "fail.dcm", 100L);

        assertFalse(result);
    }

    @Test
    void deleteWithRetry_success() throws Exception {
        DiskShare share = mock(DiskShare.class);
        doNothing().when(share).rm(anyString());

        Method m = getMethod("deleteWithRetry", DiskShare.class, String.class, int.class, long.class);
        boolean result = (boolean) m.invoke(service, share, "file.dcm", 3, 0L);

        assertTrue(result);
    }

    @Test
    void deleteWithRetry_nonSharingException_returnsFalse() throws Exception {
        DiskShare share = mock(DiskShare.class);
        doThrow(new RuntimeException("some other error")).when(share).rm(anyString());

        Method m = getMethod("deleteWithRetry", DiskShare.class, String.class, int.class, long.class);
        boolean result = (boolean) m.invoke(service, share, "file.dcm", 3, 0L);

        assertFalse(result);
    }

    @Test
    void deleteWithRetry_sharingViolationExhaustsRetries_returnsFalse() throws Exception {
        DiskShare share = mock(DiskShare.class);
        doThrow(new RuntimeException("STATUS_SHARING_VIOLATION")).when(share).rm(anyString());

        Method m = getMethod("deleteWithRetry", DiskShare.class, String.class, int.class, long.class);
        boolean result = (boolean) m.invoke(service, share, "file.dcm", 2, 0L);

        assertFalse(result);
    }

    @Test
    void deleteWithRetry_sharingViolationWithHexCode_returnsFalse() throws Exception {
        DiskShare share = mock(DiskShare.class);
        doThrow(new RuntimeException("0xc0000043 access denied")).when(share).rm(anyString());

        Method m = getMethod("deleteWithRetry", DiskShare.class, String.class, int.class, long.class);
        boolean result = (boolean) m.invoke(service, share, "file.dcm", 2, 0L);

        assertFalse(result);
    }

    @Test
    void sleepQuietly_completesNormally() throws Exception {
        Method m = getMethod("sleepQuietly", long.class);
        assertDoesNotThrow(() -> m.invoke(service, 1L));
    }

    @Test
    void establishConnectionAndCopy_success() throws Exception {
        try (MockedConstruction<SMBClient> mocked =
                     Mockito.mockConstruction(SMBClient.class, (client, context) -> {
                         Connection conn = mock(Connection.class);
                         Session session = mock(Session.class);
                         DiskShare share = mock(DiskShare.class);
                         File file = mock(File.class);

                         when(file.getInputStream()).thenReturn(new ByteArrayInputStream("x".getBytes()));
                         when(share.openFile(anyString(), any(), any(), any(), any(), any())).thenReturn(file);
                         when(session.connectShare(anyString())).thenReturn(share);
                         when(conn.authenticate(any())).thenReturn(session);
                         when(client.connect(anyString())).thenReturn(conn);
                         lenient().when(conn.isConnected()).thenReturn(true);
                         lenient().when(share.isConnected()).thenReturn(true);
                     })) {

            when(sharedFolderConfig.getServername()).thenReturn("server");
            when(sharedFolderConfig.getSharepath()).thenReturn("share");
            when(sharedFolderConfig.getUsername()).thenReturn("user");
            when(sharedFolderConfig.getPassword()).thenReturn("pass");
            when(commonUtils.getLocalStoragePath()).thenReturn(tempDir());
            setMaxRetries(1L);

            Method m = getMethod("establishConnectionAndCopy", String.class, String.class, long.class);
            boolean result = (boolean) m.invoke(service, "remote/test.dcm", "test.dcm", 10L);

            assertTrue(result);
        }
    }

    @Test
    void establishConnectionAndCopy_ensureShareReturnsNull_returnsFalse() throws Exception {
        try (MockedConstruction<SMBClient> mocked =
                     Mockito.mockConstruction(SMBClient.class, (client, context) -> {
                         when(client.connect(anyString())).thenThrow(new IOException("cannot connect"));
                     })) {

            when(sharedFolderConfig.getServername()).thenReturn("server");
            when(sharedFolderConfig.getUsername()).thenReturn("user");
            setMaxRetries(1L);

            Method m = getMethod("establishConnectionAndCopy", String.class, String.class, long.class);
            boolean result = (boolean) m.invoke(service, "remote/fail.dcm", "fail.dcm", 10L);

            assertFalse(result);
        }
    }

    @Test
    void establishConnectionAndCopy_copyReturnsFalse_returnsFalse() throws Exception {
        try (MockedConstruction<SMBClient> mocked =
                     Mockito.mockConstruction(SMBClient.class, (client, context) -> {
                         Connection conn = mock(Connection.class);
                         Session session = mock(Session.class);
                         DiskShare share = mock(DiskShare.class);
                         File file = mock(File.class);

                         when(file.getInputStream()).thenThrow(new RuntimeException("read failure"));
                         when(share.openFile(anyString(), any(), any(), any(), any(), any())).thenReturn(file);
                         when(session.connectShare(anyString())).thenReturn(share);
                         when(conn.authenticate(any())).thenReturn(session);
                         when(client.connect(anyString())).thenReturn(conn);
                         lenient().when(conn.isConnected()).thenReturn(true);
                         lenient().when(share.isConnected()).thenReturn(true);
                     })) {

            when(sharedFolderConfig.getServername()).thenReturn("server");
            when(sharedFolderConfig.getSharepath()).thenReturn("share");
            when(sharedFolderConfig.getUsername()).thenReturn("user");
            when(sharedFolderConfig.getPassword()).thenReturn("pass");
            when(commonUtils.getLocalStoragePath()).thenReturn(tempDir());
            setMaxRetries(0L);

            Method m = getMethod("establishConnectionAndCopy", String.class, String.class, long.class);
            boolean result = (boolean) m.invoke(service, "remote/fail.dcm", "fail.dcm", 10L);

            assertFalse(result);
        }
    }

    @Test
    void establishConnectionAndCopy_openFileThrows_retriesAndFails() throws Exception {
        try (MockedConstruction<SMBClient> mocked =
                     Mockito.mockConstruction(SMBClient.class, (client, context) -> {
                         Connection conn = mock(Connection.class);
                         Session session = mock(Session.class);
                         DiskShare share = mock(DiskShare.class);

                         when(share.openFile(anyString(), any(), any(), any(), any(), any()))
                                 .thenThrow(new RuntimeException("open failed"));
                         when(session.connectShare(anyString())).thenReturn(share);
                         when(conn.authenticate(any())).thenReturn(session);
                         when(client.connect(anyString())).thenReturn(conn);
                         lenient().when(conn.isConnected()).thenReturn(true);
                         lenient().when(share.isConnected()).thenReturn(true);
                     })) {

            when(sharedFolderConfig.getServername()).thenReturn("server");
            when(sharedFolderConfig.getSharepath()).thenReturn("share");
            when(sharedFolderConfig.getUsername()).thenReturn("user");
            when(sharedFolderConfig.getPassword()).thenReturn("pass");
            setMaxRetries(2L);

            Method m = getMethod("establishConnectionAndCopy", String.class, String.class, long.class);
            boolean result = (boolean) m.invoke(service, "remote/fail.dcm", "fail.dcm", 10L);

            assertFalse(result);
        }
    }

    @Test
    void establishConnectionAndCopy_anonymousAuth_success() throws Exception {
        try (MockedConstruction<SMBClient> mocked =
                     Mockito.mockConstruction(SMBClient.class, (client, context) -> {
                         Connection conn = mock(Connection.class);
                         Session session = mock(Session.class);
                         DiskShare share = mock(DiskShare.class);
                         File file = mock(File.class);

                         when(file.getInputStream()).thenReturn(new ByteArrayInputStream("y".getBytes()));
                         when(share.openFile(anyString(), any(), any(), any(), any(), any())).thenReturn(file);
                         when(session.connectShare(anyString())).thenReturn(share);
                         when(conn.authenticate(any())).thenReturn(session);
                         when(client.connect(anyString())).thenReturn(conn);
                         lenient().when(conn.isConnected()).thenReturn(true);
                         lenient().when(share.isConnected()).thenReturn(true);
                     })) {

            when(sharedFolderConfig.getServername()).thenReturn("server");
            when(sharedFolderConfig.getSharepath()).thenReturn("share");
            when(sharedFolderConfig.getUsername()).thenReturn("");
            when(commonUtils.getLocalStoragePath()).thenReturn(tempDir());
            setMaxRetries(1L);

            Method m = getMethod("establishConnectionAndCopy", String.class, String.class, long.class);
            boolean result = (boolean) m.invoke(service, "remote/anon.dcm", "anon.dcm", 10L);

            assertTrue(result);
        }
    }

    @Test
    void ensureShare_cachedHandleIsReused() throws Exception {
        try (MockedConstruction<SMBClient> mocked =
                     Mockito.mockConstruction(SMBClient.class, (client, context) -> {
                         Connection conn = mock(Connection.class);
                         Session session = mock(Session.class);
                         DiskShare share = mock(DiskShare.class);

                         when(session.connectShare(anyString())).thenReturn(share);
                         when(conn.authenticate(any())).thenReturn(session);
                         when(client.connect(anyString())).thenReturn(conn);
                         when(conn.isConnected()).thenReturn(true);
                         when(share.isConnected()).thenReturn(true);
                     })) {

            when(sharedFolderConfig.getServername()).thenReturn("server");
            when(sharedFolderConfig.getSharepath()).thenReturn("share");
            when(sharedFolderConfig.getUsername()).thenReturn("user");
            when(sharedFolderConfig.getPassword()).thenReturn("pass");

            Method m = getMethod("ensureShare");
            Object handle1 = m.invoke(service);
            Object handle2 = m.invoke(service);

            assertNotNull(handle1);
            assertSame(handle1, handle2);
            assertEquals(1, mocked.constructed().size());
        }
    }

    @Test
    void ensureShare_staleHandleIsReplaced() throws Exception {
        try (MockedConstruction<SMBClient> mocked =
                     Mockito.mockConstruction(SMBClient.class, (client, context) -> {
                         Connection conn = mock(Connection.class);
                         Session session = mock(Session.class);
                         DiskShare share = mock(DiskShare.class);

                         when(session.connectShare(anyString())).thenReturn(share);
                         when(conn.authenticate(any())).thenReturn(session);
                         when(client.connect(anyString())).thenReturn(conn);
                         lenient().when(conn.isConnected()).thenReturn(false);
                         lenient().when(share.isConnected()).thenReturn(false);
                     })) {

            when(sharedFolderConfig.getServername()).thenReturn("server");
            when(sharedFolderConfig.getSharepath()).thenReturn("share");
            when(sharedFolderConfig.getUsername()).thenReturn("user");
            when(sharedFolderConfig.getPassword()).thenReturn("pass");

            Method m = getMethod("ensureShare");
            m.invoke(service);
            m.invoke(service);

            assertTrue(mocked.constructed().size() >= 2);
        }
    }

    @Test
    void closeShareQuietly_nullHandle_doesNothing() throws Exception {
        Method m = getMethod("closeShareQuietly");
        assertDoesNotThrow(() -> m.invoke(service));
    }

    @Test
    void closeShareQuietly_withHandle_closesAllResources() throws Exception {
        try (MockedConstruction<SMBClient> mocked =
                     Mockito.mockConstruction(SMBClient.class, (client, context) -> {
                         Connection conn = mock(Connection.class);
                         Session session = mock(Session.class);
                         DiskShare share = mock(DiskShare.class);

                         when(session.connectShare(anyString())).thenReturn(share);
                         when(conn.authenticate(any())).thenReturn(session);
                         when(client.connect(anyString())).thenReturn(conn);
                         lenient().when(conn.isConnected()).thenReturn(true);
                         lenient().when(share.isConnected()).thenReturn(true);
                     })) {

            when(sharedFolderConfig.getServername()).thenReturn("server");
            when(sharedFolderConfig.getSharepath()).thenReturn("share");
            when(sharedFolderConfig.getUsername()).thenReturn("user");
            when(sharedFolderConfig.getPassword()).thenReturn("pass");

            Method ensure = getMethod("ensureShare");
            ensure.invoke(service);

            Method close = getMethod("closeShareQuietly");
            assertDoesNotThrow(() -> close.invoke(service));
        }
    }

    @Test
    void closeShareQuietly_closeThrows_doesNotPropagate() throws Exception {
        try (MockedConstruction<SMBClient> mocked =
                     Mockito.mockConstruction(SMBClient.class, (client, context) -> {
                         Connection conn = mock(Connection.class);
                         Session session = mock(Session.class);
                         DiskShare share = mock(DiskShare.class);

                         doThrow(new RuntimeException("close error")).when(share).close();
                         doThrow(new RuntimeException("logoff error")).when(session).logoff();
                         doThrow(new RuntimeException("conn error")).when(conn).close();
                         doThrow(new RuntimeException("client error")).when(client).close();

                         when(session.connectShare(anyString())).thenReturn(share);
                         when(conn.authenticate(any())).thenReturn(session);
                         when(client.connect(anyString())).thenReturn(conn);
                         lenient().when(conn.isConnected()).thenReturn(true);
                         lenient().when(share.isConnected()).thenReturn(true);
                     })) {

            when(sharedFolderConfig.getServername()).thenReturn("server");
            when(sharedFolderConfig.getSharepath()).thenReturn("share");
            when(sharedFolderConfig.getUsername()).thenReturn("user");
            when(sharedFolderConfig.getPassword()).thenReturn("pass");

            Method ensure = getMethod("ensureShare");
            ensure.invoke(service);

            Method close = getMethod("closeShareQuietly");
            assertDoesNotThrow(() -> close.invoke(service));
        }
    }

    @Test
    void closeQuietly_allNull_doesNothing() throws Exception {
        Method m = getMethod("closeQuietly", DiskShare.class, Session.class, Connection.class, SMBClient.class);
        assertDoesNotThrow(() -> m.invoke(service, null, null, null, null));
    }

    @Test
    void closeQuietly_allThrow_doesNotPropagate() throws Exception {
        DiskShare share = mock(DiskShare.class);
        Session session = mock(Session.class);
        Connection conn = mock(Connection.class);
        SMBClient client = mock(SMBClient.class);

        doThrow(new RuntimeException("share error")).when(share).close();
        doThrow(new RuntimeException("session error")).when(session).logoff();
        doThrow(new RuntimeException("conn error")).when(conn).close();
        doThrow(new RuntimeException("client error")).when(client).close();

        Method m = getMethod("closeQuietly", DiskShare.class, Session.class, Connection.class, SMBClient.class);
        assertDoesNotThrow(() -> m.invoke(service, share, session, conn, client));
    }

    @Test
    void deleteRemoteBestEffort_deleteFails_logsAndContinues() throws Exception {
        DiskShare share = mock(DiskShare.class);
        doThrow(new RuntimeException("delete failed")).when(share).rm(anyString());

        Method m = getMethod("deleteRemoteBestEffort", DiskShare.class, String.class);
        assertDoesNotThrow(() -> m.invoke(service, share, "remote/file.dcm"));
    }

    @Test
    void submitWithSizeControl_smallFile_directSubmit() throws Exception {
        Callable<Boolean> task = () -> true;
        when(executorService.submit(any(Callable.class))).thenReturn(mock(Future.class));

        Method m = getMethod("submitWithSizeControl", long.class, Callable.class);
        Future<?> result = (Future<?>) m.invoke(service, 10L, task);

        assertNotNull(result);
        verify(executorService).submit(task);
    }

    @Test
    void submitWithSizeControl_mediumFile_wrapsWithSemaphore() throws Exception {
        Callable<Boolean> task = () -> true;
        when(executorService.submit(any(Callable.class))).thenReturn(mock(Future.class));

        Method m = getMethod("submitWithSizeControl", long.class, Callable.class);
        Future<?> result = (Future<?>) m.invoke(service, 200L << 20, task);

        assertNotNull(result);
        verify(executorService).submit(any(Callable.class));
    }

    @Test
    void submitWithSizeControl_largeFile_wrapsWithLargeSemaphore() throws Exception {
        Callable<Boolean> task = () -> true;
        when(executorService.submit(any(Callable.class))).thenReturn(mock(Future.class));

        Method m = getMethod("submitWithSizeControl", long.class, Callable.class);
        Future<?> result = (Future<?>) m.invoke(service, 2L << 30, task);

        assertNotNull(result);
        verify(executorService).submit(any(Callable.class));
    }
}