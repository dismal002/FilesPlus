/*
 * Copyright (C) ExBin Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.exbin.bined.editor.android

import android.content.ContentResolver
import android.net.Uri
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import org.exbin.auxiliary.binary_data.delta.DataSource
import java.io.IOException

/**
 * Content data source for Android content resolver using native Os calls.
 *
 * @author ExBin Project (https://exbin.org)
 */
class ContentDataSource(
    private val contentResolver: ContentResolver,
    val fileUri: Uri
) : DataSource {

    private var pfd: android.os.ParcelFileDescriptor? = null

    @Synchronized
    private fun ensureOpen(): android.os.ParcelFileDescriptor {
        if (pfd == null) {
            try {
                // Try to open in read-write mode to support editing
                pfd = contentResolver.openFileDescriptor(fileUri, "rw")
            } catch (e: Exception) {
                // Fallback to read-only mode
                try {
                    pfd = contentResolver.openFileDescriptor(fileUri, "r")
                } catch (ex: Exception) {
                    throw IOException("Unable to open file descriptor for URI: $fileUri", ex)
                }
            }
            if (pfd == null) {
                throw IOException("Unable to open file descriptor for URI: $fileUri")
            }
        }
        return pfd!!
    }

    override fun getDataLength(): Long {
        return try {
            ensureOpen().statSize
        } catch (e: IOException) {
            0L
        }
    }

    override fun setDataLength(dataLength: Long) {
        try {
            Os.ftruncate(ensureOpen().fileDescriptor, dataLength)
        } catch (e: ErrnoException) {
            // Ignore if unable to set length
        } catch (e: IOException) {
            // Ignore
        }
    }

    override fun getByte(position: Long): Byte {
        return try {
            val buffer = ByteArray(1)
            val fd = ensureOpen().fileDescriptor
            val read = Os.pread(fd, buffer, 0, 1, position)
            if (read == 1) {
                buffer[0]
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    override fun setByte(position: Long, value: Byte) {
        try {
            val buffer = ByteArray(1)
            buffer[0] = value
            val fd = ensureOpen().fileDescriptor
            Os.pwrite(fd, buffer, 0, 1, position)
        } catch (e: Exception) {
            throw RuntimeException("Unable to write byte at position $position: ${e.message}", e)
        }
    }

    override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        return try {
            val fd = ensureOpen().fileDescriptor
            var totalRead = 0
            while (totalRead < length) {
                // Os.pread returns the number of bytes read
                val read = Os.pread(fd, buffer, offset + totalRead, length - totalRead, position + totalRead)
                if (read == 0) {
                    break // EOF
                }
                if (read == -1) { // Should throw exception on error, but check anyway
                    break
                }
                totalRead += read
            }
            if (totalRead == 0 && length > 0) -1 else totalRead
        } catch (e: Exception) {
            -1
        }
    }

    override fun write(position: Long, buffer: ByteArray, offset: Int, length: Int) {
        try {
            val fd = ensureOpen().fileDescriptor
            var totalWritten = 0
            while (totalWritten < length) {
                val written = Os.pwrite(fd, buffer, offset + totalWritten, length - totalWritten, position + totalWritten)
                if (written == 0) {
                     // Should not happen for write unless something is weird
                     break
                }
                totalWritten += written
            }
        } catch (e: Exception) {
            throw RuntimeException("Unable to write bytes at position $position: ${e.message}", e)
        }
    }

    override fun clearCache() {
        // No cache to clear
    }

    @Synchronized
    override fun close() {
        pfd?.close()
        pfd = null
    }

    fun getName(): String {
        return fileUri.lastPathSegment ?: "unknown"
    }
}