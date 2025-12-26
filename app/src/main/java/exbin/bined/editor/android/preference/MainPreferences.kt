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

import org.exbin.bined.editor.android.options.MainOptions
import java.util.*

/**
 * Application main preferences.
 *
 * @author ExBin Project (https://exbin.org)
 */
class MainPreferences(private val preferences: Preferences) : MainOptions {

    companion object {
        const val PREFERENCES_THEME = "theme"
        const val PREFERENCES_LOCALE_LANGUAGE = "locale.language"
        const val PREFERENCES_LOCALE_COUNTRY = "locale.country"
        const val PREFERENCES_LOCALE_VARIANT = "locale.variant"
        const val PREFERENCES_LOCALE_TAG = "locale.tag"
    }

    override fun getLocaleLanguage(): String = preferences.get(PREFERENCES_LOCALE_LANGUAGE, "")

    override fun getLocaleCountry(): String = preferences.get(PREFERENCES_LOCALE_COUNTRY, "")

    override fun getLocaleVariant(): String = preferences.get(PREFERENCES_LOCALE_VARIANT, "")

    override fun getLocaleTag(): String = preferences.get(PREFERENCES_LOCALE_TAG, "")

    override fun getTheme(): String = preferences.get(PREFERENCES_THEME, "default")

    val locale: Locale
        get() {
            val localeTagValue = getLocaleTag()
            if (localeTagValue.trim().isNotEmpty()) {
                try {
                    return Locale.forLanguageTag(localeTagValue)
                } catch (ex: SecurityException) {
                    // Ignore it in java webstart
                }
            }

            val localeLanguageValue = getLocaleLanguage()
            val localeCountryValue = getLocaleCountry()
            val localeVariantValue = getLocaleVariant()
            try {
                return Locale(localeLanguageValue, localeCountryValue, localeVariantValue)
            } catch (ex: SecurityException) {
                // Ignore it in java webstart
            }

            return Locale.ROOT
        }

    override fun setLocaleLanguage(language: String) {
        preferences.put(PREFERENCES_LOCALE_LANGUAGE, language)
    }

    override fun setLocaleCountry(country: String) {
        preferences.put(PREFERENCES_LOCALE_COUNTRY, country)
    }

    override fun setLocaleVariant(variant: String) {
        preferences.put(PREFERENCES_LOCALE_VARIANT, variant)
    }

    override fun setLocaleTag(variant: String) {
        preferences.put(PREFERENCES_LOCALE_TAG, variant)
    }

    fun setLocale(locale: Locale) {
        setLocaleTag(locale.toLanguageTag())
        setLocaleLanguage(locale.language)
        setLocaleCountry(locale.country)
        setLocaleVariant(locale.variant)
    }

    override fun setTheme(theme: String) {
        preferences.put(PREFERENCES_THEME, theme)
    }
}