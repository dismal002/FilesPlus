package com.dismal.files.provider.remote;

import com.dismal.files.provider.remote.IRemoteFileSystem;
import com.dismal.files.provider.remote.IRemoteFileSystemProvider;
import com.dismal.files.provider.remote.IRemotePosixFileAttributeView;
import com.dismal.files.provider.remote.IRemotePosixFileStore;
import com.dismal.files.provider.remote.ParcelableObject;

interface IRemoteFileService {
    IRemoteFileSystemProvider getRemoteFileSystemProviderInterface(String scheme);

    IRemoteFileSystem getRemoteFileSystemInterface(in ParcelableObject fileSystem);

    IRemotePosixFileStore getRemotePosixFileStoreInterface(in ParcelableObject fileStore);

    IRemotePosixFileAttributeView getRemotePosixFileAttributeViewInterface(
        in ParcelableObject attributeView
    );

    void setArchivePasswords(in ParcelableObject fileSystem, in List<String> passwords);

    void refreshArchiveFileSystem(in ParcelableObject fileSystem);
}
