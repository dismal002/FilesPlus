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

import java.io.FileNotFoundException
import java.io.IOException
import java.nio.channels.ReadableByteChannel
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Data source using android data content.
 *
 * @author ExBin Project (https://exbin.org)
 */
class DeltaDataPageWindow(private val data: ContentDataSource) {

    companion object {
        const val PAGE_SIZE = 1024
    }

    private val dataPages = arrayOf(DataPage(), DataPage())
    private var activeDataPage = 1

    init {
        dataPages[0].pageIndex = 0
        loadPage(0)
    }

    private fun loadPage(index: Int) {
        try {
            val pageIndex = dataPages[index].pageIndex
            val pagePosition = pageIndex * PAGE_SIZE
            val fileLength = data.dataLength
            val page = dataPages[index].page
            
            var toRead = if (pagePosition + PAGE_SIZE > fileLength) {
                (fileLength - pagePosition).toInt()
            } else {
                PAGE_SIZE
            }
            
            var offset = 0
            while (toRead > 0) {
                val red = data.read(pagePosition + offset, page, offset, toRead)
                if (red == -1) {
                    break
                }
                toRead -= red
                offset += red
            }
            
            // Clear the rest of the page if partial read or end of file
            if (offset < PAGE_SIZE) {
                java.util.Arrays.fill(page, offset, PAGE_SIZE, 0.toByte())
            }
        } catch (ex: IOException) {
            Logger.getLogger(DeltaDataPageWindow::class.java.name).log(Level.SEVERE, null, ex)
        }
    }

    fun getByte(position: Long): Byte {
        val targetPageIndex = position / PAGE_SIZE
        var index = -1
        val pageIndex1 = dataPages[0].pageIndex
        val pageIndex2 = dataPages[1].pageIndex
        
        when (targetPageIndex) {
            pageIndex1 -> index = 0
            pageIndex2 -> index = 1
        }
        
        if (index == -1) {
            val dataPage = dataPages[activeDataPage]
            dataPage.pageIndex = targetPageIndex
            loadPage(activeDataPage)
            activeDataPage = (activeDataPage + 1) and 1
            return dataPage.page[(position % PAGE_SIZE).toInt()]
        }

        return dataPages[index].page[(position % PAGE_SIZE).toInt()]
    }

    @Throws(IOException::class)
    fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        val targetPageIndex = position / PAGE_SIZE
        var index = -1
        val pageIndex1 = dataPages[0].pageIndex
        val pageIndex2 = dataPages[1].pageIndex
        
        when (targetPageIndex) {
            pageIndex1 -> index = 0
            pageIndex2 -> index = 1
        }
        
        if (index == -1) {
            val dataPage = dataPages[activeDataPage]
            dataPage.pageIndex = targetPageIndex
            loadPage(activeDataPage)
            activeDataPage = (activeDataPage + 1) and 1

            val pageOffset = (position % PAGE_SIZE).toInt()
            val red = minOf(PAGE_SIZE - pageOffset, length)
            System.arraycopy(dataPage.page, pageOffset, buffer, offset, red)
            return red
        }

        val pageOffset = (position % PAGE_SIZE).toInt()
        val red = minOf(PAGE_SIZE - pageOffset, length)
        System.arraycopy(dataPages[index].page, pageOffset, buffer, offset, red)
        return red
    }

    /**
     * Clears window cache.
     */
    fun clearCache() {
        dataPages[0].pageIndex = -1
        dataPages[1].pageIndex = -1
    }

    /**
     * Simple structure for data page.
     */
    private class DataPage {
        var pageIndex: Long = -1
        val page: ByteArray = ByteArray(PAGE_SIZE)
    }
}