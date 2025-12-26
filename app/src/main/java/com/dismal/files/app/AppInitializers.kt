/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package com.dismal.files.app

import android.os.AsyncTask
import android.os.Build
import android.webkit.WebView
import com.jakewharton.threetenabp.AndroidThreeTen
import jcifs.context.SingletonContext
import com.dismal.files.BuildConfig
import com.dismal.files.coil.initializeCoil
import com.dismal.files.filejob.fileJobNotificationTemplate
import com.dismal.files.ftpserver.ftpServerServiceNotificationTemplate
import com.dismal.files.hiddenapi.HiddenApi
import com.dismal.files.provider.FileSystemProviders
import com.dismal.files.settings.Settings
import com.dismal.files.storage.FtpServerAuthenticator
import com.dismal.files.storage.SftpServerAuthenticator
import com.dismal.files.storage.SmbServerAuthenticator
import com.dismal.files.storage.StorageVolumeListLiveData
import com.dismal.files.storage.WebDavServerAuthenticator
import com.dismal.files.theme.custom.CustomThemeHelper
import com.dismal.files.theme.night.NightModeHelper
import java.util.Properties
import com.dismal.files.provider.ftp.client.Client as FtpClient
import com.dismal.files.provider.sftp.client.Client as SftpClient
import com.dismal.files.provider.smb.client.Client as SmbClient
import com.dismal.files.provider.webdav.client.Client as WebDavClient

val appInitializers = listOf(
    ::disableHiddenApiChecks, ::initializeThreeTen,
    ::initializeWebViewDebugging, ::initializeCoil, ::initializeFileSystemProviders, ::upgradeApp,
    ::initializeLiveDataObjects, ::initializeCustomTheme, ::initializeNightMode,
    ::createNotificationChannels, ::initializeCrashlytics
)

private fun initializeCrashlytics() {
//#ifdef NONFREE
    com.dismal.files.nonfree.CrashlyticsInitializer.initialize()
//#endif
}

private fun disableHiddenApiChecks() {
    HiddenApi.disableHiddenApiChecks()
}

private fun initializeThreeTen() {
    AndroidThreeTen.init(application)
}

private fun initializeWebViewDebugging() {
    if (BuildConfig.DEBUG) {
        WebView.setWebContentsDebuggingEnabled(true)
    }
}

private fun initializeFileSystemProviders() {
    FileSystemProviders.install()
    FileSystemProviders.overflowWatchEvents = true
    // SingletonContext.init() calls NameServiceClientImpl.initCache() which connects to network.
    AsyncTask.THREAD_POOL_EXECUTOR.execute {
        SingletonContext.init(
            Properties().apply {
                setProperty("jcifs.netbios.cachePolicy", "0")
                setProperty("jcifs.smb.client.maxVersion", "SMB1")
            }
        )
    }
    FtpClient.authenticator = FtpServerAuthenticator
    SftpClient.authenticator = SftpServerAuthenticator
    SmbClient.authenticator = SmbServerAuthenticator
    WebDavClient.authenticator = WebDavServerAuthenticator
}

private fun initializeLiveDataObjects() {
    // Force initialization of LiveData objects so that it won't happen on a background thread.
    StorageVolumeListLiveData.value
    Settings.FILE_LIST_DEFAULT_DIRECTORY.value
}

private fun initializeCustomTheme() {
    CustomThemeHelper.initialize(application)
}

private fun initializeNightMode() {
    NightModeHelper.initialize(application)
}

private fun createNotificationChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notificationManager.createNotificationChannels(
            listOf(
                backgroundActivityStartNotificationTemplate.channelTemplate,
                fileJobNotificationTemplate.channelTemplate,
                ftpServerServiceNotificationTemplate.channelTemplate
            ).map { it.create(application) }
        )
    }
}
