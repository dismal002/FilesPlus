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

import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.exbin.bined.*
import org.exbin.framework.bined.BinaryStatusApi
import org.exbin.framework.bined.StatusCursorPositionFormat
import org.exbin.framework.bined.StatusDocumentSizeFormat

/**
 * Binary editor status handler.
 *
 * @author ExBin Project (https://exbin.org)
 */
class BinaryStatusHandler(private val app: AppCompatActivity) : BinaryStatusApi {

    companion object {
        const val INSERT_EDIT_MODE_LABEL = "INS"
        const val OVERWRITE_EDIT_MODE_LABEL = "OVR"
        const val READONLY_EDIT_MODE_LABEL = "RO"
        const val INPLACE_EDIT_MODE_LABEL = "INP"

        const val OCTAL_CODE_TYPE_LABEL = "OCT"
        const val DECIMAL_CODE_TYPE_LABEL = "DEC"
        const val HEXADECIMAL_CODE_TYPE_LABEL = "HEX"

        const val DEFAULT_OCTAL_SPACE_GROUP_SIZE = 4
        const val DEFAULT_DECIMAL_SPACE_GROUP_SIZE = 3
        const val DEFAULT_HEXADECIMAL_SPACE_GROUP_SIZE = 4

        private const val BR_TAG = "<br>"
    }

    private val cursorPositionFormat = StatusCursorPositionFormat()
    private val documentSizeFormat = StatusDocumentSizeFormat()
    private var octalSpaceGroupSize = DEFAULT_OCTAL_SPACE_GROUP_SIZE
    private var decimalSpaceGroupSize = DEFAULT_DECIMAL_SPACE_GROUP_SIZE
    private var hexadecimalSpaceGroupSize = DEFAULT_HEXADECIMAL_SPACE_GROUP_SIZE

    private var editOperation: EditOperation? = null
    private var caretPosition: CodeAreaCaretPosition? = null
    private var selectionRange: SelectionRange? = null
    private var documentSize: Long = 0
    private var initialDocumentSize: Long = 0

    fun updateStatus() {
        updateCaretPosition()
        updateCursorPositionToolTip()
        updateDocumentSize()
        updateDocumentSizeToolTip()
    }

    override fun setCursorPosition(caretPosition: CodeAreaCaretPosition) {
        this.caretPosition = caretPosition
        updateCaretPosition()
        updateCursorPositionToolTip()
    }

    override fun setSelectionRange(selectionRange: SelectionRange) {
        this.selectionRange = selectionRange
        updateCaretPosition()
        updateCursorPositionToolTip()
        updateDocumentSize()
        updateDocumentSizeToolTip()
    }

    override fun setCurrentDocumentSize(documentSize: Long, initialDocumentSize: Long) {
        this.documentSize = documentSize
        this.initialDocumentSize = initialDocumentSize
        updateDocumentSize()
        updateDocumentSizeToolTip()
    }

    fun setEncoding(encodingName: String) {
        val charsetTextView = app.findViewById<TextView>(R.id.charset)
        charsetTextView.text = encodingName
    }

    override fun setEditMode(editMode: EditMode, editOperation: EditOperation) {
        this.editOperation = editOperation

        val editModeLabel = app.findViewById<TextView>(R.id.editModeLabel)

        when (editMode) {
            EditMode.READ_ONLY -> {
                editModeLabel.text = READONLY_EDIT_MODE_LABEL
            }
            EditMode.EXPANDING, EditMode.CAPPED -> {
                when (editOperation) {
                    EditOperation.INSERT -> {
                        editModeLabel.text = INSERT_EDIT_MODE_LABEL
                    }
                    EditOperation.OVERWRITE -> {
                        editModeLabel.text = OVERWRITE_EDIT_MODE_LABEL
                    }
                    else -> throw CodeAreaUtils.getInvalidTypeException(editOperation)
                }
            }
            EditMode.INPLACE -> {
                editModeLabel.text = INPLACE_EDIT_MODE_LABEL
            }
            else -> throw CodeAreaUtils.getInvalidTypeException(editMode)
        }
    }

    override fun setMemoryMode(memoryMode: BinaryStatusApi.MemoryMode) {
        val memoryModeLabel = app.findViewById<TextView>(R.id.memoryModeLabel)
        memoryModeLabel.text = memoryMode.displayChar
    }

    private fun updateCaretPosition() {
        val cursorPositionLabel = app.findViewById<TextView>(R.id.cursorPositionLabel)
        if (caretPosition == null) {
            cursorPositionLabel.text = "-"
        } else {
            val labelBuilder = StringBuilder()
            val selection = selectionRange
            if (selection != null && !selection.isEmpty) {
                val first = selection.first
                val last = selection.last
                labelBuilder.append(numberToPosition(first, cursorPositionFormat.codeType))
                labelBuilder.append(" to ")
                labelBuilder.append(numberToPosition(last, cursorPositionFormat.codeType))
            } else {
                labelBuilder.append(numberToPosition(caretPosition!!.dataPosition, cursorPositionFormat.codeType))
                if (cursorPositionFormat.isShowOffset) {
                    labelBuilder.append(":")
                    labelBuilder.append(caretPosition!!.codeOffset)
                }
            }
            cursorPositionLabel.text = labelBuilder.toString()
        }
    }

    private fun updateCursorPositionToolTip() {
        // Tooltip functionality can be implemented if needed
    }

    private fun updateDocumentSize() {
        val documentSizeLabel = app.findViewById<TextView>(R.id.documentSizeLabel)

        if (documentSize == -1L) {
            documentSizeLabel.text = if (documentSizeFormat.isShowRelative) "0 (0)" else "0"
        } else {
            val labelBuilder = StringBuilder()
            val selection = selectionRange
            if (selection != null && !selection.isEmpty) {
                labelBuilder.append(numberToPosition(selection.length, documentSizeFormat.codeType))
                labelBuilder.append(" of ")
                labelBuilder.append(numberToPosition(documentSize, documentSizeFormat.codeType))
            } else {
                labelBuilder.append(numberToPosition(documentSize, documentSizeFormat.codeType))
                if (documentSizeFormat.isShowRelative) {
                    val difference = documentSize - initialDocumentSize
                    labelBuilder.append(if (difference > 0) " (+" else " (")
                    labelBuilder.append(numberToPosition(difference, documentSizeFormat.codeType))
                    labelBuilder.append(")")
                }
            }
            documentSizeLabel.text = labelBuilder.toString()
        }
    }

    private fun updateDocumentSizeToolTip() {
        // Tooltip functionality can be implemented if needed
    }

    private fun numberToPosition(value: Long, codeType: PositionCodeType): String {
        if (value == 0L) {
            return "0"
        }

        val spaceGroupSize = when (codeType) {
            PositionCodeType.OCTAL -> octalSpaceGroupSize
            PositionCodeType.DECIMAL -> decimalSpaceGroupSize
            PositionCodeType.HEXADECIMAL -> hexadecimalSpaceGroupSize
            else -> throw CodeAreaUtils.getInvalidTypeException(codeType)
        }

        var remainder = if (value > 0) value else -value
        val builder = StringBuilder()
        val base = codeType.base
        var groupSize = if (spaceGroupSize == 0) -1 else spaceGroupSize
        
        while (remainder > 0) {
            if (groupSize >= 0) {
                if (groupSize == 0) {
                    builder.insert(0, ' ')
                    groupSize = spaceGroupSize - 1
                } else {
                    groupSize--
                }
            }

            val digit = (remainder % base).toInt()
            remainder /= base
            builder.insert(0, CodeAreaUtils.UPPER_HEX_CODES[digit])
        }

        if (value < 0) {
            builder.insert(0, "-")
        }
        return builder.toString()
    }
}