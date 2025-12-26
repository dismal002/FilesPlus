package com.dismal.files.provider.remote;

import com.dismal.files.provider.remote.ParcelableException;
import com.dismal.files.util.RemoteCallback;

interface IRemotePathObservable {
    void addObserver(in RemoteCallback observer);

    void close(out ParcelableException exception);
}
