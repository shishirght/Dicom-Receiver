package com.eh.digitalpathology.dicomreceiver.service;


import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class RemoteWatcherRefreshListener implements ApplicationListener< RefreshScopeRefreshedEvent > {

    private final RemoteDirectoryWatcher remoteDirectoryWatcher;

    public RemoteWatcherRefreshListener ( RemoteDirectoryWatcher remoteDirectoryWatcher ) {
        this.remoteDirectoryWatcher = remoteDirectoryWatcher;
    }


    @Override
    public void onApplicationEvent(RefreshScopeRefreshedEvent event) {
        if (remoteDirectoryWatcher != null) {
            remoteDirectoryWatcher.watchSharedDirectory();
        }
    }
}

