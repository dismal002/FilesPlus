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

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import java.util.*

/**
 * Preferences wrapper.
 *
 * @author ExBin Project (https://exbin.org)
 */
class PreferencesWrapper(context: Context) : Preferences {

    private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    override fun flush() {
        // No-op for SharedPreferences
    }

    override fun exists(key: String): Boolean {
        return preferences.contains(key)
    }

    override fun get(key: String): Optional<String> {
        return Optional.ofNullable(preferences.getString(key, null))
    }

    override fun get(key: String, def: String): String {
        return preferences.getString(key, def) ?: def
    }

    override fun getBoolean(key: String, def: Boolean): Boolean {
        return preferences.getBoolean(key, def)
    }

    override fun getByteArray(key: String, def: ByteArray): ByteArray {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun getDouble(key: String, def: Double): Double {
        return preferences.getFloat(key, def.toFloat()).toDouble()
    }

    override fun getFloat(key: String, def: Float): Float {
        return preferences.getFloat(key, def)
    }

    override fun getInt(key: String, def: Int): Int {
        return preferences.getInt(key, def)
    }

    override fun getLong(key: String, def: Long): Long {
        return preferences.getLong(key, def)
    }

    override fun put(key: String, value: String?) {
        preferences.edit().putString(key, value).commit()
    }

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).commit()
    }

    override fun putByteArray(key: String, value: ByteArray) {
        throw UnsupportedOperationException("Not supported yet.")
    }

    override fun putDouble(key: String, value: Double) {
        preferences.edit().putFloat(key, value.toFloat()).commit()
    }

    override fun putFloat(key: String, value: Float) {
        preferences.edit().putFloat(key, value).commit()
    }

    override fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).commit()
    }

    override fun putLong(key: String, value: Long) {
        preferences.edit().putLong(key, value).commit()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).commit()
    }

    override fun sync() {
        // No-op for SharedPreferences
    }
}