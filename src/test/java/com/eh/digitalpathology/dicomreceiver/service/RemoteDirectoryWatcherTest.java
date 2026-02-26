package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.SharedFolderConfig;
import net.idauto.oss.jcifsng.vfs2.provider.SmbFileSystemConfigBuilder;
import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.impl.DefaultFileSystemConfigBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoteDirectoryWatcherTest {

    @Mock RemoteDirectoryService remoteDirectoryService;
    @Mock RedisClient redisClient;
    @Mock SharedFolderConfig sharedFolderConfig;
    @Mock ScheduledExecutorService scheduler;
    @Mock FileSystemManager fsManager;
    @Mock FileObject rootDir;

    private RemoteDirectoryWatcher watcher;

    @BeforeEach
    void setup() {
        watcher = new RemoteDirectoryWatcher(
                sharedFolderConfig,
                remoteDirectoryService,
                redisClient,
                scheduler
        );
        ReflectionTestUtils.setField(watcher, "fsManager", fsManager);
        ReflectionTestUtils.setField(watcher, "smbUrl", "/server/share");
    }

    // -------------------------------------------------
    // scanDirectoryRecursively – NEW FILE SUCCESS
    // -------------------------------------------------
    @Test
    void scanDirectoryRecursively_newFileProcessed() throws Exception {

        FileObject file = mock(FileObject.class);
        FileName name = mock(FileName.class);
        FileContent content = mock(FileContent.class);

        when(rootDir.getChildren()).thenReturn(new FileObject[]{file});
        when(file.getName()).thenReturn(name);
        when(name.getBaseName()).thenReturn("sample.txt");
        when(name.getPath()).thenReturn("/files/sample.txt");

        when(file.getType()).thenReturn(FileType.FILE);
        when(file.isHidden()).thenReturn(false);
        when(file.getContent()).thenReturn(content);
        when(content.getSize()).thenReturn(1024L);
        when(content.getLastModifiedTime()).thenReturn(1L, 1L);

        when(redisClient.isFileProcessed(anyString())).thenReturn(false);
        when(redisClient.tryLockFile(anyString(), any(Duration.class))).thenReturn(true);

        when(remoteDirectoryService.processFileEvent(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        ReflectionTestUtils.invokeMethod(watcher, "scanDirectoryRecursively", rootDir);

        verify(redisClient).markFileAsProcessed("/files/sample.txt");
        verify(remoteDirectoryService).processFileEvent(any(), any());
    }

    // -------------------------------------------------
    // scanDirectoryRecursively – HIDDEN FILE SKIPPED
    // -------------------------------------------------
    @Test
    void scanDirectoryRecursively_hiddenFileSkipped() throws Exception {

        FileObject hidden = mock(FileObject.class);
        FileName name = mock(FileName.class);

        when(rootDir.getChildren()).thenReturn(new FileObject[]{hidden});
        when(hidden.getName()).thenReturn(name);
        when(name.getBaseName()).thenReturn(".secret");
        when(name.getPath()).thenReturn("/hidden/.secret");

        ReflectionTestUtils.invokeMethod(watcher, "scanDirectoryRecursively", rootDir);

        verify(remoteDirectoryService, never()).processFileEvent(any(), any());
        verify(redisClient, never()).tryLockFile(any(), any());
    }

    // -------------------------------------------------
    // scanDirectoryRecursively – SUBFOLDER SCANNED
    // -------------------------------------------------
    @Test
    void scanDirectoryRecursively_subFolderScanned() throws Exception {

        FileObject folder = mock(FileObject.class);
        FileName name = mock(FileName.class);

        when(rootDir.getChildren()).thenReturn(new FileObject[]{folder});
        when(folder.getName()).thenReturn(name);
        when(name.getBaseName()).thenReturn("sub");
        when(name.getPath()).thenReturn("/sub");
        when(folder.getType()).thenReturn(FileType.FOLDER);
        when(folder.isHidden()).thenReturn(false);
        when(folder.getChildren()).thenReturn(new FileObject[0]);

        ReflectionTestUtils.invokeMethod(watcher, "scanDirectoryRecursively", rootDir);

        verify(folder).getChildren();
    }

    // -------------------------------------------------
    // resolveSharedFolder – SCHEDULER INVOKED
    // -------------------------------------------------
    @Test
    void resolveSharedFolder_schedulesScan() {

        ReflectionTestUtils.setField(watcher, "fsOptions", new FileSystemOptions());

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

        when(scheduler.scheduleWithFixedDelay(
                captor.capture(), eq(0L), eq(5L), eq(TimeUnit.SECONDS)))
                .thenReturn(mock(ScheduledFuture.class));

        ReflectionTestUtils.invokeMethod(watcher, "resolveSharedFolder");

        assertNotNull(captor.getValue());
    }

    // -------------------------------------------------
    // watchSharedDirectory – HAPPY PATH
    // -------------------------------------------------
    @Test
    void watchSharedDirectory_success() {

        when(sharedFolderConfig.getUsername()).thenReturn("user");
        when(sharedFolderConfig.getPassword()).thenReturn("pass");
        when(sharedFolderConfig.getServername()).thenReturn("server");
        when(sharedFolderConfig.getSharepath()).thenReturn("/share");

        try (MockedStatic<VFS> vfs = mockStatic(VFS.class);
             MockedStatic<SmbFileSystemConfigBuilder> smb = mockStatic(SmbFileSystemConfigBuilder.class);
             MockedStatic<DefaultFileSystemConfigBuilder> dfs = mockStatic(DefaultFileSystemConfigBuilder.class);
             MockedConstruction<URIBuilder> uri = mockConstruction(URIBuilder.class,
                     (b, c) -> {
                         when(b.setScheme(any())).thenReturn(b);
                         when(b.setHost(any())).thenReturn(b);
                         when(b.setPath(any())).thenReturn(b);
                         when(b.toString()).thenReturn("/server/share");
                     })) {

            vfs.when(VFS::getManager).thenReturn(fsManager);
            smb.when(SmbFileSystemConfigBuilder::getInstance)
                    .thenReturn(mock(SmbFileSystemConfigBuilder.class));
            dfs.when(DefaultFileSystemConfigBuilder::getInstance)
                    .thenReturn(mock(DefaultFileSystemConfigBuilder.class));

            watcher.watchSharedDirectory();

            verify(sharedFolderConfig).getServername();
            verify(sharedFolderConfig).getSharepath();
        }
    }
}