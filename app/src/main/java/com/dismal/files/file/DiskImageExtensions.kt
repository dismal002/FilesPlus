/*
 * Copyright (c) 2024 Dismal <dismal@dismal.dev>
 * All Rights Reserved.
 */

package com.dismal.files.file

import com.dismal.files.filelist.extension
import com.dismal.files.filelist.name

/**
 * Extensions for detecting and handling disk image files
 */

val MimeType.isDiskImage: Boolean
    get() = when (value) {
        "application/x-iso9660-image",
        "application/x-cd-image",
        "application/x-raw-disk-image",
        "application/octet-stream" -> true
        else -> false
    }

val FileItem.isDiskImage: Boolean
    get() = mimeType.isDiskImage || extension.lowercase() in DISK_IMAGE_EXTENSIONS

val FileItem.isVentoyPackage: Boolean
    get() = path.name.lowercase().startsWith("ventoy-") && (path.name.lowercase().endsWith(".tar.gz") || path.name.lowercase().endsWith(".tgz"))

private val DISK_IMAGE_EXTENSIONS = setOf(

    "img", "iso", "bin", "raw", "dd", "dmg"
)

/**
 * Check if this file is a supported disk image for flashing
 */
val FileItem.isSupportedDiskImage: Boolean
    get() = extension.lowercase() in SUPPORTED_DISK_IMAGE_EXTENSIONS

private val SUPPORTED_DISK_IMAGE_EXTENSIONS = setOf(
    "img", "iso", "bin", "raw", "dd"
)