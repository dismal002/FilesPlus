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
import android.content.DialogInterface
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AppCompatDialogFragment
import org.exbin.bined.SelectionRange
import org.exbin.bined.android.basic.CodeArea
import org.exbin.bined.editor.android.R
import java.util.*

/**
 * Edit selection dialog.
 *
 * @author ExBin Project (https://exbin.org)
 */
class EditSelectionDialog : AppCompatDialogFragment() {

    private var positiveListener: DialogInterface.OnClickListener? = null
    private lateinit var editSelectionView: android.view.View
    private lateinit var codeArea: CodeArea

    fun setPositiveListener(positiveListener: DialogInterface.OnClickListener) {
        this.positiveListener = positiveListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        codeArea = activity.findViewById(R.id.codeArea)
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(resources.getString(R.string.edit_selection))
        
        val inflater = activity.layoutInflater
        editSelectionView = inflater.inflate(R.layout.edit_selection_view, null)
        
        initializeComponents()
        
        builder.setView(editSelectionView)
        builder.setPositiveButton(resources.getString(R.string.button_ok)) { dialog, which ->
            positiveListener?.onClick(dialog, which)
        }
        builder.setNegativeButton(resources.getString(R.string.button_cancel), null)
        
        return builder.create()
    }

    private fun initializeComponents() {
        val startEditText = editSelectionView.findViewById<EditText>(R.id.startPositionText)
        val endEditText = editSelectionView.findViewById<EditText>(R.id.endPositionText)
        
        val selection = codeArea.selection
        if (!selection.isEmpty) {
            startEditText.setText(selection.first.toString())
            endEditText.setText(selection.last.toString())
        }
    }

    val selectionRange: Optional<SelectionRange>
        get() {
            return try {
                val startEditText = editSelectionView.findViewById<EditText>(R.id.startPositionText)
                val endEditText = editSelectionView.findViewById<EditText>(R.id.endPositionText)
                
                val startText = startEditText.text.toString()
                val endText = endEditText.text.toString()
                
                if (startText.isEmpty() || endText.isEmpty()) {
                    Optional.empty()
                } else {
                    val start = startText.toLong()
                    val end = endText.toLong()
                    Optional.of(SelectionRange(start, end))
                }
            } catch (e: NumberFormatException) {
                Optional.empty()
            }
        }
}