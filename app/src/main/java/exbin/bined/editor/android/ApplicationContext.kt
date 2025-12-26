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

import android.app.Application
import org.exbin.auxiliary.binary_data.delta.SegmentsRepository
import org.exbin.auxiliary.binary_data.jna.JnaBufferEditableData
import org.exbin.bined.android.basic.CodeArea
import org.exbin.bined.editor.android.preference.BinaryEditorPreferences
import org.exbin.bined.editor.android.preference.PreferencesWrapper
import org.exbin.bined.editor.android.search.SearchParameters

/**
 * Application context.
 *
 * @author ExBin Project (https://exbin.org)
 */
class ApplicationContext : Application() {

    private lateinit var appPreferences: BinaryEditorPreferences
    private val segmentsRepository = SegmentsRepository { JnaBufferEditableData() }

    private var fileHandler: BinEdFileHandler? = null
    private var searchActive = false
    private var searchParameters: SearchParameters? = null

    override fun onCreate() {
        super.onCreate()
        appPreferences = BinaryEditorPreferences(PreferencesWrapper(applicationContext))
    }

    fun getAppPreferences(): BinaryEditorPreferences = appPreferences

    fun getFileHandler(): BinEdFileHandler? = fileHandler

    fun createFileHandler(codeArea: CodeArea): BinEdFileHandler {
        val handler = BinEdFileHandler(codeArea)
        handler.segmentsRepository = segmentsRepository
        handler.setNewData(appPreferences.editorPreferences.fileHandlingMode)
        fileHandler = handler
        return handler
    }

    fun getSearchParameters(): SearchParameters? = searchParameters

    fun setSearchParameters(searchParameters: SearchParameters?) {
        this.searchParameters = searchParameters
    }

    fun isSearchActive(): Boolean = searchActive

    fun setSearchActive(searchActive: Boolean) {
        this.searchActive = searchActive
    }
}