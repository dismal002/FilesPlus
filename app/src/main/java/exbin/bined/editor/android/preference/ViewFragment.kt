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

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.TwoStatePreference
import org.exbin.bined.CodeCharactersCase
import org.exbin.bined.CodeType
import org.exbin.bined.basic.CodeAreaViewMode
import org.exbin.bined.editor.android.R
import org.exbin.bined.editor.android.SettingsActivity
import java.util.logging.Level
import java.util.logging.Logger

/**
 * View preferences fragment.
 *
 * @author ExBin Project (https://exbin.org)
 */
class ViewFragment : PreferenceFragmentCompat() {

    companion object {
        const val FONT_KEY = "font"
        const val ENCODING_KEY = "encoding"
        const val BYTES_PER_ROW_KEY = "bytes_per_row"
        const val VIEW_MODE_KEY = "view_mode"
        const val CODE_TYPE_KEY = "code_type"
        const val HEX_CHARACTERS_CASE = "hex_characters_case"
        const val CODE_COLORIZATION = "code_colorization"
        const val NONPRINTABLE_CHARACTERS = "nonprintable_characters"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.view_preferences, rootKey)

        // Load from preferences
        val activity = requireActivity() as SettingsActivity
        val appPreferences = activity.getAppPreferences()
        val codeAreaPreferences = appPreferences.codeAreaPreferences
        val fontPreferences = appPreferences.fontPreferences
        findPreference<androidx.preference.Preference>(FONT_KEY)?.summary = fontPreferences.fontSize.toString()
        val encodingPreferences = appPreferences.encodingPreferences
        findPreference<androidx.preference.Preference>(ENCODING_KEY)?.summary = encodingPreferences.defaultEncoding
        (findPreference<ListPreference>(BYTES_PER_ROW_KEY))?.value = codeAreaPreferences.maxBytesPerRow.toString()
        (findPreference<ListPreference>(VIEW_MODE_KEY))?.value = codeAreaPreferences.viewMode.name.lowercase()
        (findPreference<ListPreference>(CODE_TYPE_KEY))?.value = codeAreaPreferences.codeType.name.lowercase()
        (findPreference<ListPreference>(HEX_CHARACTERS_CASE))?.value = codeAreaPreferences.codeCharactersCase.name.lowercase()
        (findPreference<TwoStatePreference>(CODE_COLORIZATION))?.isChecked = codeAreaPreferences.isCodeColorization
        (findPreference<TwoStatePreference>(NONPRINTABLE_CHARACTERS))?.isChecked = codeAreaPreferences.isShowNonprintables
    }

    override fun onDestroy() {
        // Save to preferences
        try {
            val activity = requireActivity() as SettingsActivity
            val appPreferences = activity.getAppPreferences()
            val codeAreaPreferences = appPreferences.codeAreaPreferences
            val fontPreferences = appPreferences.fontPreferences
            // Font and encoding preferences are handled by their respective dialogs
            // fontPreferences.fontSize = (findPreference<FontPreference>(FONT_KEY))?.text?.toIntOrNull() ?: 12
            val encodingPreferences = appPreferences.encodingPreferences
            // encodingPreferences.defaultEncoding = (findPreference<EncodingPreference>(ENCODING_KEY))?.text ?: "UTF-8"
            var bytesPerRowMode = (findPreference<ListPreference>(BYTES_PER_ROW_KEY))?.value ?: "0"
            if (bytesPerRowMode == "custom") {
                // TODO Add support for custom
                bytesPerRowMode = "0"
            }
            codeAreaPreferences.maxBytesPerRow = bytesPerRowMode.toIntOrNull() ?: 0
            codeAreaPreferences.viewMode = CodeAreaViewMode.valueOf((findPreference<ListPreference>(VIEW_MODE_KEY))?.value?.uppercase() ?: "DUAL")
            codeAreaPreferences.codeType = CodeType.valueOf((findPreference<ListPreference>(CODE_TYPE_KEY))?.value?.uppercase() ?: "HEXADECIMAL")
            codeAreaPreferences.codeCharactersCase = CodeCharactersCase.valueOf((findPreference<ListPreference>(HEX_CHARACTERS_CASE))?.value?.uppercase() ?: "UPPER")
            codeAreaPreferences.isCodeColorization = (findPreference<TwoStatePreference>(CODE_COLORIZATION))?.isChecked ?: false
            codeAreaPreferences.isShowNonprintables = (findPreference<TwoStatePreference>(NONPRINTABLE_CHARACTERS))?.isChecked ?: false
        } catch (ex: IllegalArgumentException) {
            Logger.getLogger(ViewFragment::class.java.name).log(Level.SEVERE, null, ex)
        }

        super.onDestroy()
    }
}