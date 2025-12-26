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
package org.exbin.bined.editor.android.gui

import android.app.AlertDialog
import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialogFragment
import org.exbin.bined.editor.android.R

/**
 * About application dialog.
 *
 * @author ExBin Project (https://exbin.org)
 */
class AboutDialog : AppCompatDialogFragment() {

    companion object {
        private const val DONATE_LINK = "https://bined.exbin.org/?p=donate"
    }

    private var appVersion: String = ""

    fun setAppVersion(appVersion: String) {
        this.appVersion = appVersion
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(resources.getString(R.string.application_about))
        
        // Get the layout inflater
        val inflater = activity.layoutInflater
        // Inflate and set the layout for the dialog
        val aboutView = inflater.inflate(R.layout.about_view, null)
        val textView = aboutView.findViewById<TextView>(R.id.textView)
        
        val htmlString = String.format(
            resources.getString(R.string.app_about), 
            appVersion,
            "https://exbin.org", 
            "https://www.apache.org/licenses/LICENSE-2.0", 
            "https://bined.exbin.org/android"
        ).replace("\n", "<br/>")
        
        try {
            textView.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(htmlString, Html.FROM_HTML_MODE_COMPACT)
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(htmlString)
            }
        } catch (ex: NoSuchMethodError) {
            textView.setText(htmlString, TextView.BufferType.SPANNABLE)
        }
        
        textView.movementMethod = LinkMovementMethod.getInstance()
        builder.setView(aboutView)
        builder.setPositiveButton(resources.getString(R.string.button_close)) { _, _ ->
            // Close dialog
        }
        
        return builder.create()
    }
}