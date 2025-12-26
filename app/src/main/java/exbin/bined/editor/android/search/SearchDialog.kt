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
package org.exbin.bined.editor.android.search

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.Selection
import android.text.method.KeyListener
import android.text.method.TextKeyListener
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.tabs.TabLayout
import org.exbin.auxiliary.binary_data.EditableBinaryData
import org.exbin.auxiliary.binary_data.jna.JnaBufferEditableData
import org.exbin.bined.CodeAreaCaretListener
import org.exbin.bined.EditOperation
import org.exbin.bined.RowWrappingMode
import org.exbin.bined.android.basic.CodeArea
import org.exbin.bined.android.basic.color.BasicCodeAreaColorsProfile
import org.exbin.bined.editor.android.CompatUtils
import org.exbin.bined.editor.android.MainActivity
import org.exbin.bined.editor.android.R

/**
 * Search text or data dialog.
 *
 * @author ExBin Project (https://exbin.org)
 */
class SearchDialog : AppCompatDialogFragment() {

    private var lastTab = 0
    private lateinit var editText: EditText
    private lateinit var codeArea: CodeArea
    private var templateCodeArea: CodeArea? = null
    private var keyboardShown = false

    private lateinit var searchView: View
    private var searchParameters: SearchParameters? = null

    private lateinit var binarySearch: BinarySearch
    private lateinit var searchStatusListener: BinarySearchService.SearchStatusListener
    
    private lateinit var codeAreaCodeAreaCaretListener: CodeAreaCaretListener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity() as MainActivity
        binarySearch = activity.getBinarySearch()!!
        templateCodeArea = activity.getCodeArea()
        searchParameters = activity.getSearchParameters()
        searchStatusListener = activity.getSearchStatusListener()!!

        editText = EditText(activity)
        codeArea = CodeArea(activity, null)
        
        // Initialize the caret listener after codeArea is created
        codeAreaCodeAreaCaretListener = CodeAreaCaretListener { caretPosition ->
            val showKeyboard = true
            if (showKeyboard != keyboardShown) {
                keyboardShown = showKeyboard
                codeArea.requestFocus()
                codeArea.postDelayed({
                    val im = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    if (showKeyboard) {
                        // TODO im.setInputMethodAndSubtype()
                        im.showSoftInput(codeArea, InputMethodManager.SHOW_IMPLICIT)
//                        if (CompatUtils.isAndroidTV(requireContext())) {
//                            im.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
//                        }
                        val dialog = this@SearchDialog.dialog
                        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                    } else {
                        im.hideSoftInputFromWindow(codeArea.windowToken, 0)
                    }
                }, 100)
            }
        }
        
        codeArea.setContentData(JnaBufferEditableData())
        codeArea.setEditOperation(EditOperation.INSERT)
        codeArea.addCaretMovedListener(codeAreaCodeAreaCaretListener)
        codeArea.rowWrapping = RowWrappingMode.WRAPPING
        codeArea.setOnKeyListener(CodeAreaKeyListener())
        codeArea.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            if (view == codeArea) {
                if (hasFocus) {
                    codeAreaCodeAreaCaretListener.caretMoved(codeArea.activeCaretPosition)
                } else {
                    keyboardShown = false
                }
            }
        }
        
        val basicColors = codeArea.basicColors.orElse(null)
            ?: throw IllegalStateException("Missing colors profile")
        basicColors.setContext(activity)
        basicColors.reinitialize()
        codeArea.resetColors()
        codeArea.minimumHeight = 120
        
        templateCodeArea?.let { template ->
            codeArea.codeFont = template.codeFont
            codeArea.codeType = template.codeType
            codeArea.charset = template.charset
            codeArea.codeCharactersCase = template.codeCharactersCase
        }

        val builder = AlertDialog.Builder(activity)
        builder.setTitle(resources.getString(R.string.search_title))

        val inflater = activity.layoutInflater
        searchView = inflater.inflate(R.layout.search_view, null)

        val frameLayout = searchView.findViewById<FrameLayout>(R.id.frameLayout)
        frameLayout.addView(editText)
        frameLayout.nextFocusDownId = editText.id

        searchParameters?.let {
            loadSearchParameters()
        }

        val tabLayout = searchView.findViewById<TabLayout>(R.id.tabLayout)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                tabSwitched(tab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
            }
        })

        builder.setView(searchView)
        builder.setPositiveButton(R.string.button_search) { _, _ ->
            saveSearchParameters()
            binarySearch.performFind(searchParameters!!, searchStatusListener)
        }
        builder.setNegativeButton(R.string.button_cancel) { _, _ ->
            binarySearch.cancelSearch()
            binarySearch.clearSearch()
        }
        return builder.create()
    }

    private fun tabSwitched(tab: TabLayout.Tab) {
        val frameLayout = searchView.findViewById<FrameLayout>(R.id.frameLayout)
        val tabPos = tab.position
        if (tabPos != lastTab) {
            frameLayout.removeView(if (lastTab == 0) editText else codeArea)
            frameLayout.addView(if (tabPos == 0) editText else codeArea)
            frameLayout.nextFocusDownId = if (tabPos == 0) editText.id else codeArea.id
            val matchCaseSwitch = searchView.findViewById<SwitchCompat>(R.id.match_case)
            matchCaseSwitch.isEnabled = tabPos == 0
            lastTab = tabPos
        }
    }

    private fun loadSearchParameters() {
        val condition = searchParameters!!.condition
        editText.setText(condition.searchText)
        val data = codeArea.contentData as EditableBinaryData
        data.clear()
        condition.getBinaryData()?.let { binaryData ->
            data.insert(0, binaryData)
        }
        codeAreaCodeAreaCaretListener.caretMoved(codeArea.activeCaretPosition)

        if (condition.searchMode == SearchCondition.SearchMode.TEXT) {
            // Should work automatically, but force for now
            keyboardShown = false
        } else {
            val tabLayout = searchView.findViewById<TabLayout>(R.id.tabLayout)
            val binaryTab = tabLayout.getTabAt(1)!!
            tabLayout.selectTab(binaryTab)
            tabSwitched(binaryTab)
        }
        val matchCaseSwitch = searchView.findViewById<SwitchCompat>(R.id.match_case)
        matchCaseSwitch.isChecked = searchParameters!!.isMatchCase
        val backwardDirectionSwitch = searchView.findViewById<SwitchCompat>(R.id.backward_direction)
        backwardDirectionSwitch.isChecked = searchParameters!!.searchDirection == SearchParameters.SearchDirection.BACKWARD
        val multipleMatchesSwitch = searchView.findViewById<SwitchCompat>(R.id.multiple_matches)
        multipleMatchesSwitch.isChecked = searchParameters!!.matchMode == SearchParameters.MatchMode.MULTIPLE
        val fromCursorSwitch = searchView.findViewById<SwitchCompat>(R.id.from_cursor)
        fromCursorSwitch.isChecked = searchParameters!!.isSearchFromCursor
    }

    private fun saveSearchParameters() {
        val searchCondition = SearchCondition()
        searchCondition.searchMode = if (lastTab == 0) SearchCondition.SearchMode.TEXT else SearchCondition.SearchMode.BINARY
        searchCondition.searchText = editText.text.toString()
        val data = JnaBufferEditableData()
        data.insert(0, codeArea.contentData)
        searchCondition.setBinaryData(data)
        searchParameters = SearchParameters()
        searchParameters!!.condition = searchCondition
        val matchCaseSwitch = searchView.findViewById<SwitchCompat>(R.id.match_case)
        searchParameters!!.isMatchCase = matchCaseSwitch.isChecked
        val backwardDirectionSwitch = searchView.findViewById<SwitchCompat>(R.id.backward_direction)
        searchParameters!!.searchDirection = if (backwardDirectionSwitch.isChecked) SearchParameters.SearchDirection.BACKWARD else SearchParameters.SearchDirection.FORWARD
        val multipleMatchesSwitch = searchView.findViewById<SwitchCompat>(R.id.multiple_matches)
        searchParameters!!.matchMode = if (multipleMatchesSwitch.isChecked) SearchParameters.MatchMode.MULTIPLE else SearchParameters.MatchMode.SINGLE
        val fromCursorSwitch = searchView.findViewById<SwitchCompat>(R.id.from_cursor)
        searchParameters!!.isSearchFromCursor = fromCursorSwitch.isChecked
        val activity = requireActivity() as MainActivity
        activity.setSearchParameters(searchParameters!!)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        saveSearchParameters()
    }

    private inner class CodeAreaKeyListener : View.OnKeyListener {

        private val keyListener: KeyListener = TextKeyListener(TextKeyListener.Capitalize.NONE, false)
        private val editable: Editable = Editable.Factory.getInstance().newEditable("")

        init {
            editable.clear()
            Selection.setSelection(editable, 0, 0)
        }

        override fun onKey(view: View, keyCode: Int, keyEvent: KeyEvent): Boolean {
            if (!codeArea.isFocused) {
                val currentFocus = requireActivity().currentFocus
                if (currentFocus != null) {
                    return currentFocus.dispatchKeyEvent(keyEvent)
                }
                return false
            }

            if (CompatUtils.isAndroidTV(codeArea.context)) {
                if (keyboardShown && keyEvent.keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
                    return false
                }

                if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                        codeArea.postDelayed({
                            val im = codeArea.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            im.showSoftInput(codeArea, InputMethodManager.SHOW_IMPLICIT, null)
//                            im.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
                        }, 100)

                        return true
                    } else if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                        val fromCursorView = searchView.findViewById<View>(R.id.from_cursor)
                        fromCursorView?.requestFocus()
                    }
                }
            }

            try {
                when (keyEvent.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (keyEvent.keyCode == KeyEvent.KEYCODE_DEL || keyEvent.keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
                            editable.clear()
                            codeArea.commandHandler.keyPressed(keyEvent)
                        } else {
                            keyListener.onKeyDown(view, editable, keyCode, keyEvent)
                            processKeys(keyEvent)
                        }
                    }
                    KeyEvent.ACTION_UP -> {
                        editable.clear()
                        if (keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                            codeArea.showContextMenu()
                        } else if (keyEvent.keyCode != KeyEvent.KEYCODE_DEL && keyEvent.keyCode != KeyEvent.KEYCODE_FORWARD_DEL) {
                            // TODO Do this on key up?
                            codeArea.commandHandler.keyPressed(keyEvent)
                        }
                    }
                    else -> {
                        keyListener.onKeyOther(view, editable, keyEvent)
                        processKeys(keyEvent)
                    }
                }
                return true
            } catch (ex: Exception) {
                // ignore
            }
            return false
        }

        private fun processKeys(keyEvent: KeyEvent) {
            val outputCharsLength = editable.length
            if (outputCharsLength > 0) {
                for (i in 0 until outputCharsLength) {
                    codeArea.commandHandler.keyTyped(editable[i].code, keyEvent)
                }
                editable.clear()
            }
        }
    }
}