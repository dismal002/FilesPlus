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

import android.app.LocaleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.lang.reflect.InvocationTargetException

/**
 * Compatibility utilities.
 *
 * @author ExBin Project (https://exbin.org)
 */
object CompatUtils {

    fun setApplicationLocales(context: Context, languageLocaleList: LocaleListCompat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                .setApplicationLocales(languageLocaleList.unwrap() as LocaleList)
            return
        }

        try {
            val setApplicationLocalesMethod = AppCompatDelegate::class.java
                .getMethod("setApplicationLocales", LocaleListCompat::class.java)
            setApplicationLocalesMethod.invoke(null, languageLocaleList)
        } catch (e: NoSuchMethodException) {
            // No switching available
        } catch (e: InvocationTargetException) {
            // No switching available
        } catch (e: IllegalAccessException) {
            // No switching available
        }
    }

    fun getApplicationLocales(context: Context): LocaleListCompat {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeList = context.getSystemService(LocaleManager::class.java).applicationLocales
            return LocaleListCompat.wrap(localeList)
        }

        try {
            val getApplicationLocalesMethod = AppCompatDelegate::class.java.getMethod("getApplicationLocales")
            val result = getApplicationLocalesMethod.invoke(null)
            if (result is LocaleListCompat) {
                return result
            }
        } catch (e: NoSuchMethodException) {
            // No switching available
        } catch (e: InvocationTargetException) {
            // No switching available
        } catch (e: IllegalAccessException) {
            // No switching available
        }
        return LocaleListCompat.getEmptyLocaleList()
    }

    fun enableEdgeToEdge(activity: ComponentActivity) {
        try {
            val edgeClass = Class.forName("androidx.activity.EdgeToEdge")
            val enableMethod = edgeClass.getMethod("enable", ComponentActivity::class.java)
            enableMethod.invoke(null, activity)
        } catch (e: NoSuchMethodException) {
            // No switching available
        } catch (e: InvocationTargetException) {
            // No switching available
        } catch (e: IllegalAccessException) {
            // No switching available
        } catch (e: ClassNotFoundException) {
            // No switching available
        }
    }

    fun isAndroidTV(context: Context): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}