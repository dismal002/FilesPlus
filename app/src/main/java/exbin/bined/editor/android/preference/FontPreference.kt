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
import android.widget.SeekBar
import android.widget.TextView
import org.exbin.bined.android.Font
import org.exbin.bined.editor.android.R

/**
 * Font preference utility.
 *
 * @author ExBin Project (https://exbin.org)
 */
object FontPreference {

    fun showFontSelectionDialog(context: Context, currentFont: Font, onFontSelected: (Font) -> Unit) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.select_font_size)
        
        // Create a simple font size selector
        val view = android.view.LayoutInflater.from(context).inflate(R.layout.font_selection_view, null)
        val sizeLabel = view.findViewById<TextView>(R.id.fontSizeLabel)
        val sizeSeekBar = view.findViewById<SeekBar>(R.id.fontSizeSeekBar)
        
        // Set up seek bar for font size (8-72)
        sizeSeekBar.min = 8
        sizeSeekBar.max = 72
        sizeSeekBar.progress = currentFont.size
        sizeLabel.text = "Font Size: ${currentFont.size}"
        
        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sizeLabel.text = "Font Size: $progress"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        builder.setView(view)
        builder.setPositiveButton(R.string.button_ok) { _, _ ->
            val newFont = Font()
            newFont.size = sizeSeekBar.progress
            onFontSelected(newFont)
        }
        builder.setNegativeButton(R.string.button_cancel, null)
        builder.show()
    }
}