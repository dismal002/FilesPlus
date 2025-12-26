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
package org.exbin.bined.editor.android.preference

import org.exbin.bined.basic.EnterKeyHandlingMode
import org.exbin.bined.basic.TabKeyHandlingMode
import org.exbin.bined.editor.android.options.DataInspectorMode
import org.exbin.bined.editor.android.options.EditorOptions
import org.exbin.bined.editor.android.options.KeysPanelMode
import org.exbin.framework.bined.FileHandlingMode
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Binary editor preferences.
 *
 * @author ExBin Project (https://exbin.org)
 */
class EditorPreferences(private val preferences: Preferences) : EditorOptions {

    companion object {
        const val PREFERENCES_FILE_HANDLING_MODE = "fileHandlingMode"
        const val PREFERENCES_KEYS_PANEL_MODE = "keysPanelMode"
        const val PREFERENCES_ENTER_KEY_HANDLING_MODE = "enterKeyHandlingMode"
        const val PREFERENCES_TAB_KEY_HANDLING_MODE = "tabKeyHandlingMode"
        const val PREFERENCES_DATA_INSPECTOR_MODE = "dataInspectorMode"
    }

    override fun getFileHandlingMode(): FileHandlingMode {
        val defaultFileHandlingMode = FileHandlingMode.MEMORY
        return try {
            FileHandlingMode.valueOf(preferences.get(PREFERENCES_FILE_HANDLING_MODE, defaultFileHandlingMode.name).uppercase())
        } catch (ex: IllegalArgumentException) {
            Logger.getLogger(EditorPreferences::class.java.name).log(Level.SEVERE, null, ex)
            defaultFileHandlingMode
        }
    }

    override fun getKeysPanelMode(): KeysPanelMode {
        val defaultKeysPanelMode = KeysPanelMode.SMALL
        return try {
            KeysPanelMode.valueOf(preferences.get(PREFERENCES_KEYS_PANEL_MODE, defaultKeysPanelMode.name).uppercase())
        } catch (ex: IllegalArgumentException) {
            Logger.getLogger(EditorPreferences::class.java.name).log(Level.SEVERE, null, ex)
            defaultKeysPanelMode
        }
    }

    override fun getEnterKeyHandlingMode(): EnterKeyHandlingMode {
        val defaultValue = EnterKeyHandlingMode.PLATFORM_SPECIFIC
        return try {
            EnterKeyHandlingMode.valueOf(preferences.get(PREFERENCES_ENTER_KEY_HANDLING_MODE, defaultValue.name).uppercase())
        } catch (ex: IllegalArgumentException) {
            defaultValue
        }
    }

    override fun getTabKeyHandlingMode(): TabKeyHandlingMode {
        val defaultValue = TabKeyHandlingMode.PLATFORM_SPECIFIC
        return try {
            TabKeyHandlingMode.valueOf(preferences.get(PREFERENCES_TAB_KEY_HANDLING_MODE, defaultValue.name).uppercase())
        } catch (ex: IllegalArgumentException) {
            defaultValue
        }
    }

    override fun getDataInspectorMode(): DataInspectorMode {
        val defaultDataInspectorMode = DataInspectorMode.LANDSCAPE
        return try {
            DataInspectorMode.valueOf(preferences.get(PREFERENCES_DATA_INSPECTOR_MODE, defaultDataInspectorMode.name).uppercase())
        } catch (ex: IllegalArgumentException) {
            Logger.getLogger(EditorPreferences::class.java.name).log(Level.SEVERE, null, ex)
            defaultDataInspectorMode
        }
    }

    override fun setKeysPanelMode(keysPanelMode: KeysPanelMode) {
        preferences.put(PREFERENCES_KEYS_PANEL_MODE, keysPanelMode.name)
    }

    override fun setFileHandlingMode(fileHandlingMode: FileHandlingMode) {
        preferences.put(PREFERENCES_FILE_HANDLING_MODE, fileHandlingMode.name)
    }

    override fun setEnterKeyHandlingMode(enterKeyHandlingMode: EnterKeyHandlingMode) {
        preferences.put(PREFERENCES_ENTER_KEY_HANDLING_MODE, enterKeyHandlingMode.name.lowercase())
    }

    override fun setTabKeyHandlingMode(tabKeyHandlingMode: TabKeyHandlingMode) {
        preferences.put(PREFERENCES_TAB_KEY_HANDLING_MODE, tabKeyHandlingMode.name.lowercase())
    }

    override fun setDataInspectorMode(dataInspectorMode: DataInspectorMode) {
        preferences.put(PREFERENCES_DATA_INSPECTOR_MODE, dataInspectorMode.name.lowercase())
    }
}