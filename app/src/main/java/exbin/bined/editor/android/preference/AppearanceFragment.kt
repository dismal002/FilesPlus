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

import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import org.exbin.bined.editor.android.CompatUtils
import org.exbin.bined.editor.android.MainActivity
import org.exbin.bined.editor.android.R
import org.exbin.bined.editor.android.SettingsActivity
import org.exbin.bined.editor.android.options.Theme

/**
 * Settings appearance fragment.
 *
 * @author ExBin Project (https://exbin.org)
 */
class AppearanceFragment : PreferenceFragmentCompat() {

    companion object {
        const val LANGUAGE_KEY = "language"
        const val THEME_KEY = "theme"
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.main_preferences, rootKey)

        // Load from preferences
        val activity = requireActivity() as SettingsActivity
        val mainPreferences = activity.getAppPreferences().mainPreferences
        val languagePreference = findPreference<ListPreference>(LANGUAGE_KEY)!!
        languagePreference.setOnPreferenceChangeListener { _, newValue ->
            val language = newValue as String
            // Dynamically change language
            val locales = MainActivity.getLanguageLocaleList(if ("default" == language) "" else language)
            CompatUtils.setApplicationLocales(activity, locales)

            val resources = resources
            try {
                // Update title for possibly switched language
                val configuration = resources.configuration
                configuration.setLocales(CompatUtils.getApplicationLocales(activity).unwrap() as android.os.LocaleList)
                val newResources = requireContext().createConfigurationContext(configuration).resources
                activity.title = newResources.getString(R.string.pref_header_appearance)
            } catch (tw: Throwable) {
                // Might fail on older versions with: Could not find class 'android.os.LocaleList'
            }

            true
        }
        val localeTag = mainPreferences.localeTag
        languagePreference.value = if (localeTag.isEmpty()) "default" else localeTag

        val themePreference = findPreference<ListPreference>(THEME_KEY)!!
        themePreference.setOnPreferenceChangeListener { _, newValue ->
            // Dynamically change theme
            when {
                Theme.DARK.name.equals(newValue as String, ignoreCase = true) -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }
                Theme.LIGHT.name.equals(newValue, ignoreCase = true) -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
                else -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }
            }
            true
        }
        themePreference.value = mainPreferences.theme
    }

    override fun onDestroy() {
        // Save to preferences
        val activity = requireActivity() as SettingsActivity
        val appPreferences = activity.getAppPreferences()
        val mainPreferences = appPreferences.mainPreferences
        val value = findPreference<ListPreference>(LANGUAGE_KEY)!!.value
        mainPreferences.localeTag = if ("default" == value) "" else value
        mainPreferences.theme = findPreference<ListPreference>(THEME_KEY)!!.value

        super.onDestroy()
    }
}