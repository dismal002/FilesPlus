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

import android.app.AlertDialog
import android.content.Context
import android.widget.ArrayAdapter
import org.exbin.bined.editor.android.R
import java.nio.charset.Charset

/**
 * Encoding preference utility.
 *
 * @author ExBin Project (https://exbin.org)
 */
object EncodingPreference {

    fun showEncodingSelectionDialog(context: Context, currentEncoding: String, onEncodingSelected: (String) -> Unit) {
        val availableCharsets = Charset.availableCharsets().keys.toTypedArray()
        val currentIndex = availableCharsets.indexOf(currentEncoding).takeIf { it >= 0 } ?: 0

        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.select_encoding)
        builder.setSingleChoiceItems(availableCharsets, currentIndex) { dialog, which ->
            val selectedEncoding = availableCharsets[which]
            onEncodingSelected(selectedEncoding)
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.button_cancel, null)
        builder.show()
    }
}