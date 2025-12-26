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

import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.exbin.bined.editor.android.options.DataInspectorMode
import org.exbin.bined.editor.android.options.KeysPanelMode
import org.exbin.bined.editor.android.preference.BinaryEditorPreferences
import org.exbin.bined.editor.android.preference.HeaderFragment
import org.exbin.bined.editor.android.preference.PreferencesWrapper
import org.exbin.framework.bined.FileHandlingMode

/**
 * Settings activity.
 *
 * @author ExBin Project (https://exbin.org)
 */
class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    companion object {
        private const val TITLE_TAG = "settingsActivityTitle"
    }

    private var appPreferences: BinaryEditorPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            CompatUtils.enableEdgeToEdge(this)
            window.decorView.setPadding(0, 0, 0, navigationBarHeight)
        }

        super.onCreate(savedInstanceState)
        appPreferences = getAppPreferences()
        val inflater = layoutInflater
        val view = inflater.inflate(R.layout.settings_activity, null)

        // Fix for legacy variant, comment out for playstore release
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            view.findViewById<android.view.View>(R.id.settings).setPadding(0, statusBarHeight, 0, 0)
        }

        setContentView(view)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, HeaderFragment(), "header_fragment")
                .commit()
        } else {
            title = savedInstanceState.getCharSequence(TITLE_TAG)
        }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.title_activity_settings)
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    fun getAppPreferences(): BinaryEditorPreferences {
        return appPreferences ?: BinaryEditorPreferences(PreferencesWrapper(applicationContext))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save current activity title so we can set it again after a configuration change
        outState.putCharSequence(TITLE_TAG, title)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        }

        super.onSupportNavigateUp()
        finish()
        return true
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val fragmentManager = supportFragmentManager
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = fragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        )
        fragment.arguments = args
        fragmentManager.setFragmentResultListener("requestKey", fragment) { requestKey, result ->
            caller.parentFragmentManager.setFragmentResult(requestKey, result)
        }
        // Replace the existing Fragment with the new Fragment
        fragmentManager.beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commit()
        title = pref.title
        return true
    }

    override fun finish() {
        val fragment = supportFragmentManager.findFragmentByTag("header_fragment") as? HeaderFragment
        // Save to preferences
        fragment?.let {
            val editorPreferences = getAppPreferences().editorPreferences
            editorPreferences.setFileHandlingMode(FileHandlingMode.valueOf(
                (it.findPreference<ListPreference>(HeaderFragment.FILE_HANDLING_MODE)?.value ?: "").uppercase()
            ))
            editorPreferences.setKeysPanelMode(KeysPanelMode.valueOf(
                (it.findPreference<ListPreference>(HeaderFragment.KEYS_PANEL_MODE)?.value ?: "").uppercase()
            ))
            editorPreferences.setDataInspectorMode(DataInspectorMode.valueOf(
                (it.findPreference<ListPreference>(HeaderFragment.DATA_INSPECTOR_MODE)?.value ?: "").uppercase()
            ))
        }

        super.finish()
    }

    private val statusBarHeight: Int
        get() {
            val resources = resources
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            return if (resourceId > 0) {
                resources.getDimensionPixelSize(resourceId)
            } else 0
        }

    private val navigationBarHeight: Int
        get() {
            val resources = resources
            val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
            return if (resourceId > 0) {
                resources.getDimensionPixelSize(resourceId)
            } else 0
        }
}