/*
 * Copyright (c) 2024 Dismal <dismal@dismal.dev>
 * All Rights Reserved.
 */

package com.dismal.files.ventoy

import android.content.Context
import com.dismal.files.provider.archive.archiver.ReadArchive
import java8.nio.charset.StandardCharsets
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException

class VentoyPackageHelper(private val context: Context) {
    
    suspend fun extractImages(
        packageInputStream: InputStream,
        callback: (bootImg: ByteArray, coreImg: ByteArray, ventoyDiskImg: InputStream, ventoyDiskImgSize: Long) -> Unit
    ) {
        val reader = ReadArchive(packageInputStream, emptyList())
        var bootImg: ByteArray? = null
        var coreImg: ByteArray? = null
        var ventoyDiskImgStream: InputStream? = null
        var ventoyDiskImgSize: Long = 0
        
        try {
            var entry = reader.readEntry(StandardCharsets.UTF_8)
            while (entry != null) {
                when {
                    entry.name.endsWith("boot/boot.img") -> {
                        bootImg = readEntryContent(reader)
                    }
                    entry.name.endsWith("boot/core.img.xz") -> {
                        val xzContent = readEntryContent(reader)
                        coreImg = decompressXz(xzContent)
                    }
                    entry.name.endsWith("ventoy/ventoy.disk.img.xz") -> {
                        val xzContent = readEntryContent(reader)
                        // For the large disk image, we'll decompress it on the fly or read it all into memory if small enough.
                        // ventoy.disk.img is usually 32MB. 32MB is fine for memory on modern Android.
                        val decompressed = decompressXz(xzContent)
                        ventoyDiskImgStream = decompressed.inputStream()
                        ventoyDiskImgSize = decompressed.size.toLong()
                    }
                }
                
                if (bootImg != null && coreImg != null && ventoyDiskImgStream != null) {
                    callback(bootImg, coreImg, ventoyDiskImgStream, ventoyDiskImgSize)
                    return
                }
                
                entry = reader.readEntry(StandardCharsets.UTF_8)
            }
        } finally {
            reader.close()
        }
        
        throw IOException("Required Ventoy images not found in package")
    }
    
    private fun readEntryContent(reader: ReadArchive): ByteArray {
        val output = ByteArrayOutputStream()
        val inputStream = reader.newDataInputStream()
        val buffer = ByteArray(8192)
        var read = inputStream.read(buffer)
        while (read != -1) {
            output.write(buffer, 0, read)
            read = inputStream.read(buffer)
        }
        return output.toByteArray()
    }
    
    private fun decompressXz(xzData: ByteArray): ByteArray {
        // Use libarchive to decompress XZ
        val reader = ReadArchive(xzData.inputStream(), emptyList())
        try {
            val entry = reader.readEntry(StandardCharsets.UTF_8)
            if (entry != null) {
                return readEntryContent(reader)
            }
        } finally {
            reader.close()
        }
        throw IOException("Failed to decompress XZ data")
    }
}
