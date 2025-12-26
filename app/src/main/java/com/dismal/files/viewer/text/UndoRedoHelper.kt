/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package com.dismal.files.viewer.text

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.util.*

class UndoRedoHelper(private val editText: EditText) {
    private val undoStack = Stack<TextChange>()
    private val redoStack = Stack<TextChange>()
    private var isUndoRedoInProgress = false
    private var lastChangeTime = 0L
    private val changeDelayMs = 1000L // Group changes within 1 second

    private val textWatcher = object : TextWatcher {
        private var beforeText = ""
        private var beforeStart = 0
        private var beforeCount = 0

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            if (isUndoRedoInProgress) return
            beforeText = s?.toString() ?: ""
            beforeStart = start
            beforeCount = count
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            // Not used
        }

        override fun afterTextChanged(s: Editable?) {
            if (isUndoRedoInProgress) return
            
            val currentTime = System.currentTimeMillis()
            val newText = s?.toString() ?: ""
            
            // Create text change
            val change = TextChange(
                beforeText = beforeText,
                afterText = newText,
                start = beforeStart,
                beforeCount = beforeCount,
                afterCount = newText.length - beforeText.length + beforeCount
            )
            
            // Group changes if they're close in time and position
            if (undoStack.isNotEmpty() && 
                currentTime - lastChangeTime < changeDelayMs &&
                canGroupChanges(undoStack.peek(), change)) {
                // Update the last change instead of adding a new one
                val lastChange = undoStack.pop()
                val groupedChange = TextChange(
                    beforeText = lastChange.beforeText,
                    afterText = change.afterText,
                    start = minOf(lastChange.start, change.start),
                    beforeCount = lastChange.beforeCount + change.beforeCount - lastChange.afterCount,
                    afterCount = change.afterCount
                )
                undoStack.push(groupedChange)
            } else {
                undoStack.push(change)
                // Limit undo stack size
                if (undoStack.size > 100) {
                    undoStack.removeAt(0)
                }
            }
            
            // Clear redo stack when new changes are made
            redoStack.clear()
            lastChangeTime = currentTime
        }
    }

    init {
        editText.addTextChangedListener(textWatcher)
    }

    private fun canGroupChanges(lastChange: TextChange, newChange: TextChange): Boolean {
        // Group if changes are adjacent or overlapping
        val lastEnd = lastChange.start + lastChange.afterCount
        val newEnd = newChange.start + newChange.afterCount
        return kotlin.math.abs(lastEnd - newChange.start) <= 1 || 
               kotlin.math.abs(newEnd - lastChange.start) <= 1
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        if (!canUndo()) return
        
        isUndoRedoInProgress = true
        val change = undoStack.pop()
        redoStack.push(change)
        
        editText.setText(change.beforeText)
        editText.setSelection(change.start)
        isUndoRedoInProgress = false
    }

    fun redo() {
        if (!canRedo()) return
        
        isUndoRedoInProgress = true
        val change = redoStack.pop()
        undoStack.push(change)
        
        editText.setText(change.afterText)
        editText.setSelection(change.start + change.afterCount)
        isUndoRedoInProgress = false
    }

    fun clearHistory() {
        undoStack.clear()
        redoStack.clear()
    }

    private data class TextChange(
        val beforeText: String,
        val afterText: String,
        val start: Int,
        val beforeCount: Int,
        val afterCount: Int
    )
}