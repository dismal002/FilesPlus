/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package com.dismal.files.viewer.text

import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.widget.EditText
import androidx.core.content.ContextCompat
import com.dismal.files.R
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class FindReplaceHelper(private val editText: EditText) {
    private var matches = mutableListOf<MatchResult>()
    private var currentMatchIndex = -1
    private var highlightSpans = mutableListOf<BackgroundColorSpan>()
    private var currentHighlightSpan: BackgroundColorSpan? = null
    
    private val highlightColor by lazy {
        ContextCompat.getColor(editText.context, R.color.text_editor_find_highlight)
    }
    
    private val currentHighlightColor by lazy {
        ContextCompat.getColor(editText.context, R.color.text_editor_find_current_highlight)
    }

    fun findMatches(query: String, useRegex: Boolean): Int {
        clearHighlights()
        matches.clear()
        currentMatchIndex = -1
        
        if (query.isEmpty()) {
            return 0
        }
        
        val text = editText.text.toString()
        if (text.isEmpty()) {
            return 0
        }
        
        try {
            if (useRegex) {
                val pattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE)
                val matcher = pattern.matcher(text)
                while (matcher.find()) {
                    matches.add(MatchResult(matcher.start(), matcher.end()))
                }
            } else {
                var startIndex = 0
                while (true) {
                    val index = text.indexOf(query, startIndex, ignoreCase = true)
                    if (index == -1) break
                    matches.add(MatchResult(index, index + query.length))
                    startIndex = index + 1
                }
            }
            
            highlightMatches()
            if (matches.isNotEmpty()) {
                currentMatchIndex = 0
                highlightCurrentMatch()
            }
            
        } catch (e: PatternSyntaxException) {
            // Invalid regex pattern
            return 0
        }
        
        return matches.size
    }
    
    private fun highlightMatches() {
        val spannable = SpannableString(editText.text)
        
        for (match in matches) {
            val span = BackgroundColorSpan(highlightColor)
            spannable.setSpan(span, match.start, match.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            highlightSpans.add(span)
        }
        
        editText.setText(spannable)
    }
    
    private fun highlightCurrentMatch() {
        if (currentMatchIndex < 0 || currentMatchIndex >= matches.size) return
        
        val spannable = editText.text as? Spannable ?: return
        val match = matches[currentMatchIndex]
        
        // Remove previous current highlight
        currentHighlightSpan?.let { spannable.removeSpan(it) }
        
        // Add new current highlight
        currentHighlightSpan = BackgroundColorSpan(currentHighlightColor)
        spannable.setSpan(currentHighlightSpan, match.start, match.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        // Scroll to match
        editText.setSelection(match.start, match.end)
    }
    
    fun findNext(): Int {
        if (matches.isEmpty()) return -1
        
        currentMatchIndex = (currentMatchIndex + 1) % matches.size
        highlightCurrentMatch()
        return currentMatchIndex
    }
    
    fun findPrevious(): Int {
        if (matches.isEmpty()) return -1
        
        currentMatchIndex = if (currentMatchIndex <= 0) matches.size - 1 else currentMatchIndex - 1
        highlightCurrentMatch()
        return currentMatchIndex
    }
    
    fun replaceCurrent(replacement: String, useRegex: Boolean): Boolean {
        if (currentMatchIndex < 0 || currentMatchIndex >= matches.size) return false
        
        val match = matches[currentMatchIndex]
        val text = editText.text.toString()
        
        val actualReplacement = if (useRegex) {
            try {
                val originalQuery = getOriginalQuery(text, match)
                val pattern = Pattern.compile(getLastQuery(), Pattern.CASE_INSENSITIVE)
                pattern.matcher(originalQuery).replaceFirst(replacement)
            } catch (e: Exception) {
                replacement
            }
        } else {
            replacement
        }
        
        // Replace text
        val newText = text.substring(0, match.start) + actualReplacement + text.substring(match.end)
        editText.setText(newText)
        
        // Update cursor position
        val newCursorPos = match.start + actualReplacement.length
        editText.setSelection(newCursorPos)
        
        return true
    }
    
    fun replaceAll(query: String, replacement: String, useRegex: Boolean): Int {
        val text = editText.text.toString()
        if (text.isEmpty() || query.isEmpty()) return 0
        
        try {
            val newText = if (useRegex) {
                val pattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE)
                pattern.matcher(text).replaceAll(replacement)
            } else {
                text.replace(query, replacement, ignoreCase = true)
            }
            
            val replacementCount = matches.size
            editText.setText(newText)
            clearHighlights()
            
            return replacementCount
        } catch (e: PatternSyntaxException) {
            return 0
        }
    }
    
    fun clearHighlights() {
        val spannable = editText.text as? Spannable ?: return
        
        for (span in highlightSpans) {
            spannable.removeSpan(span)
        }
        currentHighlightSpan?.let { spannable.removeSpan(it) }
        
        highlightSpans.clear()
        currentHighlightSpan = null
    }
    
    fun getCurrentMatchIndex(): Int = currentMatchIndex
    
    fun getMatchCount(): Int = matches.size
    
    private var lastQuery = ""
    
    fun setLastQuery(query: String) {
        lastQuery = query
    }
    
    private fun getLastQuery(): String = lastQuery
    
    private fun getOriginalQuery(text: String, match: MatchResult): String {
        return text.substring(match.start, match.end)
    }
    
    private data class MatchResult(val start: Int, val end: Int)
}