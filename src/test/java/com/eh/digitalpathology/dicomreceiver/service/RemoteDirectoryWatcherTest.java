package com.eh.digitalpathology.dicomreceiver.service;

import com.eh.digitalpathology.dicomreceiver.config.SharedFolderConfig;
import net.idauto.oss.jcifsng.vfs2.provider.SmbFileSystemConfigBuilder;
import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.impl.DefaultFileSystemConfigBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
        watcher = new RemoteDirectoryWatcher(sharedFolderConfig, remoteDirectoryService, redisClient, scheduler);
        ReflectionTestUtils.setField(watcher, "fsManager", fsManager);
        ReflectionTestUtils.setField(watcher, "smbUrl", "/server/share");
    }

    private FileObject mockSkipFile(String baseName) throws Exception {
        FileObject file = mock(FileObject.class);
        FileName name = mock(FileName.class);
        when(file.getName()).thenReturn(name);
        when(name.getBaseName()).thenReturn(baseName);
        return file;
    }

    private FileObject mockFullFile(String baseName, String path) throws Exception {
        FileObject file = mock(FileObject.class);
        FileName name = mock(FileName.class);
        when(file.getName()).thenReturn(name);
        when(name.getBaseName()).thenReturn(baseName);
        when(name.getPath()).thenReturn(path);
        return file;
    }

    @Test
    void scanDirectoryRecursively_nullChildren_returnsEarly() throws Exception {
        FileName dirName = mock(FileName.class);
        when(rootDir.getName()).thenReturn(dirName);
        when(dirName.getPath()).thenReturn("/root");
        when(rootDir.getChildren()).thenReturn(null);

        ReflectionTestUtils.invokeMethod(watcher, "scanDirectoryRecursively", rootDir);

        verify(remoteDirectoryService, never()).processFileEvent(any(), any());
    }

    @Test
    void scanDirectoryRecursively_emptyChildren_returnsEarly() throws Exception {
        FileName dirName = mock(FileName.class);
        when(rootDir.getName()).thenReturn(dirName);
        when(dirName.getPath()).thenReturn("/root");
        when(rootDir.getChildren()).thenReturn(new FileObject[0]);

        ReflectionTestUtils.invokeMethod(watcher, "scanDirectoryRecursively", rootDir);

        verify(remoteDirectoryService, never()).processFileEvent(any(), any());
    }

    @Test
    void scanDirectoryRecursively_exceptionDuringScan_isHandledGracefully() throws Exception {
        FileName dirName = mock(FileName.class);
        when(rootDir.getName()).thenReturn(dirName);
        when(rootDir.getChildren()).thenThrow(new FileSystemException("io error"));

        ReflectionTestUtils.invokeMethod(watcher, "scanDirectoryRecursively", rootDir);

        verify(remoteDirectoryService, never()).processFileEvent(any(), any());
    }

    @Test
    void scanDirectoryRecursively_logScanSummaryIfDue_triggersWhenOverdue() throws Exception {
        ReflectionTestUtils.setField(watcher, "lastLogTime", System.currentTimeMillis() - 70_000);

        FileObject file = mockFullFile("stable.dcm", "/files/stable.dcm");
        when(rootDir.getChildren()).thenReturn(new FileObject[]{file});
        when(file.getType()).thenReturn(FileType.FILE);
        when(file.isHidden()).thenReturn(false);

        FileContent content = mock(FileContent.class);
        when(file.getContent()).thenReturn(content);
        when(content.getSize()).thenReturn(512L);
        when(content.getLastModifiedTime()).thenReturn(1L, 1L);

        when(redisClient.isFileProcessed(anyString())).thenReturn(false);
        when(redisClient.tryLockFile(anyString(), any())).thenReturn(true);
        when(remoteDirectoryService.processFileEvent(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        ReflectionTestUtils.invokeMethod(watcher, "scanDirectoryRecursively", rootDir);

        verify(redisClient).markFileAsProcessed("/files/stable.dcm");
    }

    @Test
    void processChild_nullChild_returnsEarly() {
        ReflectionTestUtils.invokeMethod(watcher, "processChild", (Object) null);
        verifyNoInteractions(remoteDirectoryService, redisClient);
    }

    @Test
    void processChild_hiddenDotFile_skipped() throws Exception {
        FileObject file = mockSkipFile(".hidden");
        ReflectionTestUtils.invokeMethod(watcher, "processChild", file);
        verify(remoteDirectoryService, never()).processFileEvent(any(), any());
    }

    @Test
    void processChild_tildePrefix_skipped() throws Exception {
        FileObject file = mockSkipFile("~$doc.docx");
        ReflectionTestUtils.invokeMethod(watcher, "processChild", file);
        verify(remoteDirectoryService, never()).processFileEvent(any(), any());
    }

    @Test
    void processChild_thumbsDb_skipped() throws Exception {
        FileObject file = mockSkipFile("Thumbs.db");
        ReflectionTestUtils.invokeMethod(watcher, "processChild", file);
        verify(remoteDirectoryService, never()).processFileEvent(any(), any());
    }

    @Test
    void processChild_tmpFile_skipped() throws Exception {
        FileObject file = mockSkipFile("file.tmp");
        ReflectionTestUtils.invokeMethod(watcher, "processChild", file);
        verify(remoteDirectoryService, never()).processFileEvent(any(), any());
    }

    @Test
    void processChild_partFile_skipped() throws Exception {
        FileObject file = mockSkipFile("file.part");
        ReflectionTestUtils.invokeMethod(watcher, "processChild", file);
        verify(remoteDirectoryService, never()).processFileEvent(any(), any());
    }

    @Test
    void processChild_underscorePrefix_skipped() throws Exception {
        FileObject file = mockSkipFile("._resource");
        ReflectionTestUtils.invokeMethod(watcher, "processChild", file);
        verify(remoteDirectoryService, never()).processFileEvent(any(), any());
    }

    @Test
    void processChild_crdownloadFile_skipped() throws Exception {
        FileObject file = mockSkipFile("file.crdownload");
        ReflectionTestUtils.invokeMethod(watcher, "processChild", file);
        verify(remoteDirectoryService, never()).processFileEvent(any(), any());
    }

    @Test
    void processChild_getTypeSafe_throwsFileSystemException_returnsNull() throws Exception {
        FileObject file = mockFullFile("file.dcm", "/dir/file.dcm");
        when(file.getType()).thenThrow(new FileSystemException("type error"));

        ReflectionTestUtils.invokeMethod(watcher, "processChild", file);

        verify(remoteDirectoryService, never()).processFileEvent(any(), any());
    }

    @Test
    void processChild_isHiddenSafe_throwsFileSystemException_treatedAsNotHidden() throws Exception {
        FileObject file = mockFullFile("file.dcm", "/dir/file.dcm");
        when(file.getType()).thenReturn(FileType.FILE);
        when(file.isHidden()).thenThrow(new FileSystemException("hidden check failed"));

        FileContent content = mock(FileContent.class);
        when(file.getContent()).thenReturn(content);
        when(content.getSize()).thenReturn(512L);
        when(content.getLastModifiedTime()).thenReturn(1L, 1L);

        when(redisClient.isFileProcessed(anyString())).thenReturn(false);
        when(redisClient.tryLockFile(anyString(), any())).thenReturn(true);
        when(remoteDirectoryService.processFileEvent(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        ReflectionTestUtils.invokeMethod(watcher, "processChild", file);

        verify(remoteDirectoryService).processFileEvent(any(), any());
    }

    @Test
    void processChild_hiddenFile_skipped() throws Exception {
        FileObject file = mockFullFile("visible.dcm", "/dir/visible.dcm");
        when(file.getType()).thenReturn(FileType.FILE);
        when(file.isHidden()).thenReturn(true);

        ReflectionTestUtils.invokeMethod(watcher, "processChild", file);

        verify(remoteDirectoryService, never()).processFileEvent(any(), any());
    }

    @Test
    void processChild_folder_recursivelyScanned() throws Exception {
        FileObject folder = mock(FileObject.class);
        FileName name = mock(FileName.class);
        when(folder.getName()).thenReturn(name);
        when(name.getBaseName()).thenReturn("subdir");
        when(name.getPath()).thenReturn("/dir/subdir");
        when(folder.getType()).thenReturn(FileType.FOLDER);
        when(folder.isHidden()).thenReturn(false);
        when(folder.getChildren()).thenReturn(new FileObject[0]);

        ReflectionTestUtils.invokeMethod(watcher, "processChild", folder);

        verify(folder).getChildren();
    }


    @Test
    void attachCompletionHandler_successResult_marksProcessedAndReleasesLock() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ReflectionTestUtils.invokeMethod(watcher, "attachCompletionHandler", future, "/files/file.dcm");
        future.complete(true);

        verify(redisClient).markFileAsProcessed("/files/file.dcm");
        verify(redisClient).releaseFileLock("/files/file.dcm");
    }

    @Test
    void attachCompletionHandler_falseResult_releasesLockWithoutMarking() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ReflectionTestUtils.invokeMethod(watcher, "attachCompletionHandler", future, "/files/file.dcm");
        future.complete(false);

        verify(redisClient, never()).markFileAsProcessed(anyString());
        verify(redisClient).releaseFileLock("/files/file.dcm");
    }

    @Test
    void attachCompletionHandler_exceptionResult_releasesLockWithoutMarking() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ReflectionTestUtils.invokeMethod(watcher, "attachCompletionHandler", future, "/files/file.dcm");
        future.completeExceptionally(new RuntimeException("async failure"));

        verify(redisClient, never()).markFileAsProcessed(anyString());
        verify(redisClient).releaseFileLock("/files/file.dcm");
    }

    @Test
    void attachCompletionHandler_innerExceptionInHandler_releasesLock() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        doThrow(new RuntimeException("mark failed")).when(redisClient).markFileAsProcessed(anyString());

        ReflectionTestUtils.invokeMethod(watcher, "attachCompletionHandler", future, "/files/file.dcm");
        future.complete(true);

        verify(redisClient).releaseFileLock("/files/file.dcm");
    }

    @Test
    void computeAdaptiveTtl_smallFile_returnsMinTtl() throws Exception {
        FileObject file = mock(FileObject.class);
        FileContent content = mock(FileContent.class);
        when(file.getContent()).thenReturn(content);
        when(content.getSize()).thenReturn(1024L);

        Duration ttl = ReflectionTestUtils.invokeMethod(watcher, "computeAdaptiveTtl", file);

        assertEquals(Duration.ofSeconds(120), ttl);
    }

    @Test
    void computeAdaptiveTtl_largeFile_returnsClampedMaxTtl() throws Exception {
        FileObject file = mock(FileObject.class);
        FileContent content = mock(FileContent.class);
        when(file.getContent()).thenReturn(content);
        when(content.getSize()).thenReturn(500L * 1024 * 1024 * 1024);

        Duration ttl = ReflectionTestUtils.invokeMethod(watcher, "computeAdaptiveTtl", file);

        assertEquals(Duration.ofSeconds(3600), ttl);
    }

    @Test
    void computeAdaptiveTtl_exceptionReadingSize_returnsDefaultFiveMinutes() throws Exception {
        FileObject file = mock(FileObject.class);
        when(file.getContent()).thenThrow(new RuntimeException("content error"));

        Duration ttl = ReflectionTestUtils.invokeMethod(watcher, "computeAdaptiveTtl", file);

        assertEquals(Duration.ofMinutes(5), ttl);
    }

    @Test
    void isFileStable_stableFile_returnsTrue() throws Exception {
        FileObject file = mock(FileObject.class);
        FileContent content = mock(FileContent.class);
        when(file.getContent()).thenReturn(content);
        when(content.getSize()).thenReturn(512L);
        when(content.getLastModifiedTime()).thenReturn(100L, 100L);

        Boolean result = ReflectionTestUtils.invokeMethod(watcher, "isFileStable", file);

        assertTrue(result);
    }

    @Test
    void isFileStable_sizeChanged_returnsFalse() throws Exception {
        FileObject file = mock(FileObject.class);
        FileContent content = mock(FileContent.class);
        when(file.getContent()).thenReturn(content);
        when(content.getSize()).thenReturn(512L, 1024L);
        when(content.getLastModifiedTime()).thenReturn(100L);

        Boolean result = ReflectionTestUtils.invokeMethod(watcher, "isFileStable", file);

        assertFalse(result);
    }

    @Test
    void isFileStable_exceptionThrown_returnsFalse() throws Exception {
        FileObject file = mock(FileObject.class);
        when(file.getContent()).thenThrow(new RuntimeException("content unavailable"));

        Boolean result = ReflectionTestUtils.invokeMethod(watcher, "isFileStable", file);

        assertFalse(result);
    }

    @Test
    void isFileStable_interruptedException_returnsFalse() throws Exception {
        FileObject file = mock(FileObject.class);
        FileContent content = mock(FileContent.class);
        when(file.getContent()).thenReturn(content);
        when(content.getSize()).thenReturn(512L);
        when(content.getLastModifiedTime()).thenReturn(100L);

        Thread testThread = new Thread(() -> {
            Thread.currentThread().interrupt();
            Boolean result = ReflectionTestUtils.invokeMethod(watcher, "isFileStable", file);
            assertFalse(result);
        });
        testThread.start();
        testThread.join();
    }

    @Test
    void resolveSharedFolder_schedulesWithFixedDelay() {
        ReflectionTestUtils.setField(watcher, "fsOptions", new FileSystemOptions());
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(captor.capture(), eq(0L), eq(5L), eq(TimeUnit.SECONDS)))
                .thenReturn(mock(ScheduledFuture.class));

        ReflectionTestUtils.invokeMethod(watcher, "resolveSharedFolder");

        assertNotNull(captor.getValue());
    }

    @Test
    void resolveSharedFolder_scheduledRunnableResolveAndScans() throws Exception {
        FileSystemOptions fsOptions = new FileSystemOptions();
        ReflectionTestUtils.setField(watcher, "fsOptions", fsOptions);

        FileObject remoteDir = mock(FileObject.class);
        FileName dirName = mock(FileName.class);
        when(remoteDir.getName()).thenReturn(dirName);
        when(dirName.getPath()).thenReturn("/root");
        when(fsManager.resolveFile("/server/share", fsOptions)).thenReturn(remoteDir);
        when(remoteDir.getChildren()).thenReturn(new FileObject[0]);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(captor.capture(), eq(0L), eq(5L), eq(TimeUnit.SECONDS)))
                .thenReturn(mock(ScheduledFuture.class));

        ReflectionTestUtils.invokeMethod(watcher, "resolveSharedFolder");
        captor.getValue().run();

        verify(fsManager).resolveFile("/server/share", fsOptions);
    }

    @Test
    void resolveSharedFolder_runnableExceptionHandledGracefully() throws Exception {
        FileSystemOptions fsOptions = new FileSystemOptions();
        ReflectionTestUtils.setField(watcher, "fsOptions", fsOptions);

        when(fsManager.resolveFile(anyString(), any())).thenThrow(new FileSystemException("connect failed"));

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.scheduleWithFixedDelay(captor.capture(), eq(0L), eq(5L), eq(TimeUnit.SECONDS)))
                .thenReturn(mock(ScheduledFuture.class));

        ReflectionTestUtils.invokeMethod(watcher, "resolveSharedFolder");

        assertDoesNotThrow(() -> captor.getValue().run());
    }

    @Test
    void watchSharedDirectory_withCredentials_setsAuthenticator() {
        when(sharedFolderConfig.getUsername()).thenReturn("user");
        when(sharedFolderConfig.getPassword()).thenReturn("pass");
        when(sharedFolderConfig.getServername()).thenReturn("server");
        when(sharedFolderConfig.getSharepath()).thenReturn("/share");

        try (MockedStatic<VFS> vfs = mockStatic(VFS.class);
             MockedStatic<SmbFileSystemConfigBuilder> smb = mockStatic(SmbFileSystemConfigBuilder.class);
             MockedStatic<DefaultFileSystemConfigBuilder> dfs = mockStatic(DefaultFileSystemConfigBuilder.class);
             MockedConstruction<URIBuilder> uri = mockConstruction(URIBuilder.class, (b, c) -> {
                 when(b.setScheme(any())).thenReturn(b);
                 when(b.setHost(any())).thenReturn(b);
                 when(b.setPath(any())).thenReturn(b);
                 when(b.toString()).thenReturn("/server/share");
             })) {

            vfs.when(VFS::getManager).thenReturn(fsManager);
            smb.when(SmbFileSystemConfigBuilder::getInstance).thenReturn(mock(SmbFileSystemConfigBuilder.class));
            DefaultFileSystemConfigBuilder dfsInstance = mock(DefaultFileSystemConfigBuilder.class);
            dfs.when(DefaultFileSystemConfigBuilder::getInstance).thenReturn(dfsInstance);

            watcher.watchSharedDirectory();

            verify(dfsInstance).setUserAuthenticator(any(), any());
        }
    }

    @Test
    void watchSharedDirectory_withoutCredentials_skipsAuthenticator() {
        when(sharedFolderConfig.getUsername()).thenReturn("");
        when(sharedFolderConfig.getServername()).thenReturn("server");
        when(sharedFolderConfig.getSharepath()).thenReturn("/share");

        try (MockedStatic<VFS> vfs = mockStatic(VFS.class);
             MockedStatic<SmbFileSystemConfigBuilder> smb = mockStatic(SmbFileSystemConfigBuilder.class);
             MockedStatic<DefaultFileSystemConfigBuilder> dfs = mockStatic(DefaultFileSystemConfigBuilder.class);
             MockedConstruction<URIBuilder> uri = mockConstruction(URIBuilder.class, (b, c) -> {
                 when(b.setScheme(any())).thenReturn(b);
                 when(b.setHost(any())).thenReturn(b);
                 when(b.setPath(any())).thenReturn(b);
                 when(b.toString()).thenReturn("/server/share");
             })) {

            vfs.when(VFS::getManager).thenReturn(fsManager);
            smb.when(SmbFileSystemConfigBuilder::getInstance).thenReturn(mock(SmbFileSystemConfigBuilder.class));
            DefaultFileSystemConfigBuilder dfsInstance = mock(DefaultFileSystemConfigBuilder.class);
            dfs.when(DefaultFileSystemConfigBuilder::getInstance).thenReturn(dfsInstance);

            watcher.watchSharedDirectory();

            verify(dfsInstance, never()).setUserAuthenticator(any(), any());
        }
    }

    @Test
    void watchSharedDirectory_exceptionThrown_isHandledGracefully() {
        when(sharedFolderConfig.getUsername()).thenThrow(new RuntimeException("config error"));
        when(sharedFolderConfig.getServername()).thenReturn("server");
        when(sharedFolderConfig.getSharepath()).thenReturn("/share");

        assertDoesNotThrow(() -> watcher.watchSharedDirectory());
    }
}