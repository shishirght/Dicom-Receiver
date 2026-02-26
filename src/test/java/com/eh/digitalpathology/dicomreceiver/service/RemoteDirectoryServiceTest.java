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

    // ---------------------------------------------------------
    // processFileEvent
    // ---------------------------------------------------------
    @Test
    void processFileEvent_shouldSubmitTasks() throws Exception {
        FileObject file = mock(FileObject.class);
        FileName name = mock(FileName.class);
        FileContent content = mock(FileContent.class);

        when(file.getName()).thenReturn(name);
        when(name.getPath()).thenReturn("/share/test.dcm");
       lenient().when(name.getBaseName()).thenReturn("test.dcm");
        when(file.getContent()).thenReturn(content);
        when(content.getSize()).thenReturn(10L);

        when(executorService.submit(any(Callable.class)))
                .thenReturn(mock(Future.class));
        when(executorService.submit(any(Runnable.class)))
                .thenReturn(mock(Future.class));

        CompletableFuture<Boolean> result =
                service.processFileEvent(file, "/share");

        assertNotNull(result);
        verify(executorService, atLeastOnce()).submit((Callable<Object>) any());
    }

    // ---------------------------------------------------------
    // copyRemoteFileToLocal (SUCCESS)
    // ---------------------------------------------------------
    @Test
    void copyRemoteFileToLocal_success() throws Exception {
        File remoteFile = mock(File.class);
        byte[] data = "hello".getBytes();

        when(remoteFile.getInputStream())
                .thenReturn(new ByteArrayInputStream(data));
        when(commonUtils.getLocalStoragePath())
                .thenReturn(Files.createTempDirectory("local").toString());

        Method method = RemoteDirectoryService.class
                .getDeclaredMethod("copyRemoteFileToLocal",
                        File.class, String.class, long.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(
                service, remoteFile, "test.dcm", data.length);

        assertTrue(result);
    }

    // ---------------------------------------------------------
    // copyRemoteFileToLocal (FAILURE)
    // ---------------------------------------------------------
    @Test
    void copyRemoteFileToLocal_failure() throws Exception {
        File remoteFile = mock(File.class);

        when(remoteFile.getInputStream())
                .thenThrow(new RuntimeException("IO error"));
        when(commonUtils.getLocalStoragePath())
                .thenReturn(Files.createTempDirectory("local").toString());

        Method method = RemoteDirectoryService.class
                .getDeclaredMethod("copyRemoteFileToLocal",
                        File.class, String.class, long.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(
                service, remoteFile, "fail.dcm", 100L);

        assertFalse(result);
    }


    @Test
    void establishConnectionAndCopy_success() throws Exception {

        try (MockedConstruction<SMBClient> mocked =
                     Mockito.mockConstruction(SMBClient.class,
                             (client, context) -> {

                                 Connection conn = mock(Connection.class);
                                 Session session = mock(Session.class);
                                 DiskShare share = mock(DiskShare.class);
                                 File file = mock(File.class);

                                 when(file.getInputStream())
                                         .thenReturn(new ByteArrayInputStream("x".getBytes()));

                                 when(share.openFile(anyString(), any(), any(), any(), any(), any()))
                                         .thenReturn(file);
                                 when(session.connectShare(anyString()))
                                         .thenReturn(share);
                                 when(conn.authenticate(any()))
                                         .thenReturn(session);
                                 when(client.connect(anyString()))
                                         .thenReturn(conn);
                             })) {

            when(sharedFolderConfig.getServername()).thenReturn("server");
            when(sharedFolderConfig.getSharepath()).thenReturn("share");
            when(sharedFolderConfig.getUsername()).thenReturn("user");
            when(sharedFolderConfig.getPassword()).thenReturn("pass");
            when(commonUtils.getLocalStoragePath())
                    .thenReturn(Files.createTempDirectory("local").toString());

            Field retries = RemoteDirectoryService.class
                    .getDeclaredField("maxRetries");
            retries.setAccessible(true);
            retries.set(service, 1L);

            Method method = RemoteDirectoryService.class
                    .getDeclaredMethod("establishConnectionAndCopy",
                            String.class, String.class, long.class);
            method.setAccessible(true);

            boolean result = (boolean) method.invoke(
                    service, "remote/test.dcm", "test.dcm", 10L);

            assertTrue(result);
        }
    }
}