package com.dismal.files.provider.remote;

import com.dismal.files.provider.remote.ParcelableException;

interface IRemoteFileSystem {
    void close(out ParcelableException exception);
}
