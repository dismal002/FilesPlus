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
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatDialogFragment
import org.exbin.bined.PositionCodeType
import org.exbin.bined.android.basic.CodeArea
import org.exbin.bined.editor.android.R
import org.exbin.bined.editor.android.RelativePositionMode
import org.exbin.bined.editor.android.SwitchableBase

/**
 * Go to position dialog.
 *
 * @author ExBin Project (https://exbin.org)
 */
class GoToPositionDialog : AppCompatDialogFragment() {

    private var positionListener: DialogInterface.OnClickListener? = null
    private lateinit var goToPositionView: View

    private var cursorPosition: Long = 0
    private var maxPosition: Long = 0
    private val positionSwitchableBase = SwitchableBase()
    private var relativePositionMode = RelativePositionMode.FROM_START

    private lateinit var codeArea: CodeArea

    fun setPositiveListener(positionListener: DialogInterface.OnClickListener) {
        this.positionListener = positionListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        codeArea = activity.findViewById(R.id.codeArea)
        val builder = AlertDialog.Builder(activity)
        builder.setTitle(resources.getString(R.string.go_to_position))
        
        // Get the layout inflater
        val inflater = activity.layoutInflater
        // Inflate and set the layout for the dialog
        goToPositionView = inflater.inflate(R.layout.go_to_position_view, null)

        // Initialize the dialog components
        initializeComponents()
        
        builder.setView(goToPositionView)
        builder.setPositiveButton(resources.getString(R.string.button_ok)) { dialog, which ->
            positionListener?.onClick(dialog, which)
        }
        builder.setNegativeButton(resources.getString(R.string.button_cancel), null)
        
        return builder.create()
    }

    private fun initializeComponents() {
        // Initialize position input and radio buttons
        val positionEditText = goToPositionView.findViewById<EditText>(R.id.positionText)
        val fromStartRadio = goToPositionView.findViewById<RadioButton>(R.id.positionFromStartRadioButton)
        val fromCurrentRadio = goToPositionView.findViewById<RadioButton>(R.id.positionRelativeToCursorRadioButton)
        val fromEndRadio = goToPositionView.findViewById<RadioButton>(R.id.positionFromEndRadioButton)

        // Set up current values
        cursorPosition = codeArea.activeCaretPosition.dataPosition
        maxPosition = codeArea.dataSize

        // Set up text watcher for position validation
        positionEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePosition()
            }
        })

        // Set up radio button listeners
        fromStartRadio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) relativePositionMode = RelativePositionMode.FROM_START
        }
        fromCurrentRadio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) relativePositionMode = RelativePositionMode.FROM_CURSOR
        }
        fromEndRadio.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) relativePositionMode = RelativePositionMode.FROM_END
        }

        // Set default values
        fromStartRadio.isChecked = true
        positionEditText.setText("0")
    }

    private fun validatePosition() {
        // Basic validation logic
        val dialog = dialog as? AlertDialog
        val positiveButton = dialog?.getButton(AlertDialog.BUTTON_POSITIVE)
        
        try {
            val position = targetPosition
            positiveButton?.isEnabled = position >= 0 && position <= maxPosition
        } catch (e: NumberFormatException) {
            positiveButton?.isEnabled = false
        }
    }

    val targetPosition: Long
        get() {
            val positionEditText = goToPositionView.findViewById<EditText>(R.id.positionText)
            val positionText = positionEditText.text.toString()
            
            if (positionText.isEmpty()) return 0
            
            val inputPosition = positionText.toLong()
            
            return when (relativePositionMode) {
                RelativePositionMode.FROM_START -> inputPosition
                RelativePositionMode.FROM_CURSOR -> cursorPosition + inputPosition
                RelativePositionMode.FROM_END -> maxPosition - inputPosition
            }
        }
}