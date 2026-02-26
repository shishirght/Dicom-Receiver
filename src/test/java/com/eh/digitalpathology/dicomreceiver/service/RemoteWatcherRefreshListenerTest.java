package com.eh.digitalpathology.dicomreceiver.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoteWatcherRefreshListenerTest  {

    @Mock
    private RemoteDirectoryWatcher remoteDirectoryWatcher;
    @Mock
    private RemoteWatcherRefreshListener listener;

    @BeforeEach
    void setup() {
        remoteDirectoryWatcher = mock(RemoteDirectoryWatcher.class);
        listener = new RemoteWatcherRefreshListener(remoteDirectoryWatcher);
    }

    @Test
    void testOnApplicationEvent_ShouldInvokeWatchSharedDirectory() {
        RefreshScopeRefreshedEvent event = mock(RefreshScopeRefreshedEvent.class);

        listener.onApplicationEvent(event);

        verify(remoteDirectoryWatcher, times(1)).watchSharedDirectory();
    }

    @Test
    void testOnApplicationEvent_ShouldNotThrow_WhenWatcherIsNull() {
        RemoteWatcherRefreshListener nullListener = new RemoteWatcherRefreshListener(null);
        RefreshScopeRefreshedEvent event = mock(RefreshScopeRefreshedEvent.class);
        assertDoesNotThrow(() -> nullListener.onApplicationEvent(event));
    }


}