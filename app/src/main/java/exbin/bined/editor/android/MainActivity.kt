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

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.text.Editable
import android.text.Selection
import android.text.method.KeyListener
import android.text.method.TextKeyListener
import android.view.ContextMenu
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.DialogFragment
import com.rustamg.filedialogs.FileDialog
import com.rustamg.filedialogs.OpenFileDialog
import com.rustamg.filedialogs.SaveFileDialog
import org.exbin.auxiliary.binary_data.delta.DeltaDocument
import org.exbin.bined.*
import org.exbin.bined.android.CodeAreaAndroidUtils
import org.exbin.bined.android.Font
import org.exbin.bined.android.basic.CodeArea
import org.exbin.bined.android.basic.color.BasicCodeAreaColorsProfile
import org.exbin.bined.android.capability.ColorAssessorPainterCapable
import org.exbin.bined.basic.BasicCodeAreaSection
import org.exbin.bined.basic.CodeAreaViewMode
import org.exbin.bined.editor.android.gui.AboutDialog
import org.exbin.bined.editor.android.gui.EditSelectionDialog
import org.exbin.bined.editor.android.gui.GoToPositionDialog
import org.exbin.bined.editor.android.inspector.BasicValuesInspector
import org.exbin.bined.editor.android.inspector.BasicValuesPositionColorModifier
import org.exbin.bined.editor.android.options.DataInspectorMode
import org.exbin.bined.editor.android.options.KeysPanelMode
import org.exbin.bined.editor.android.options.Theme
import org.exbin.bined.editor.android.preference.BinaryEditorPreferences
import org.exbin.bined.editor.android.preference.EncodingPreference
import org.exbin.bined.editor.android.preference.FontPreference
import org.exbin.bined.editor.android.search.*
import org.exbin.bined.highlight.android.*
import org.exbin.bined.operation.BinaryDataUndoRedoChangeListener
import org.exbin.bined.operation.android.CodeAreaOperationCommandHandler
import org.exbin.framework.bined.BinEdCodeAreaAssessor
import org.exbin.framework.bined.BinaryStatusApi
import java.io.File
import java.lang.reflect.Method
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Main activity.
 *
 * @author ExBin Project (https://exbin.org)
 */
class MainActivity : AppCompatActivity(), FileDialog.OnFileSelectedListener {

    companion object {
        private const val DOUBLE_BACK_KEY_INTERVAL = 3000
        private const val SELECTION_START_POPUP_ID = 1
        private const val SELECTION_END_POPUP_ID = 2
        private const val CLEAR_SELECTION_POPUP_ID = 3
        private const val CUT_ACTION_POPUP_ID = 4
        private const val COPY_ACTION_POPUP_ID = 5
        private const val PASTE_ACTION_POPUP_ID = 6
        private const val DELETE_ACTION_POPUP_ID = 7
        private const val SELECT_ALL_ACTION_POPUP_ID = 8
        private const val COPY_AS_CODE_ACTION_POPUP_ID = 9
        private const val PASTE_FROM_CODE_ACTION_POPUP_ID = 10
        private const val GO_TO_SIDE_PANEL_POPUP_ID = 11
        private const val OPEN_MAIN_MENU_POPUP_ID = 12
        private const val SHOW_KEYBOARD_MENU_POPUP_ID = 13
        private const val STORAGE_PERMISSION_CODE = 1
        
        fun getLanguageLocaleList(language: String): LocaleListCompat {
            return if (language.isEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language)
            }
        }
    }

    private lateinit var fileHandler: BinEdFileHandler
    private lateinit var codeArea: CodeArea
    private lateinit var basicValuesInspector: BasicValuesInspector
    private lateinit var appPreferences: BinaryEditorPreferences

    private lateinit var toolbar: Toolbar
    private lateinit var keyPanel: View
    private lateinit var basicValuesInspectorView: View
    private var menu: Menu? = null
    private val binaryStatus = BinaryStatusHandler(this)
    private lateinit var binarySearch: BinarySearch
    private lateinit var searchStatusPanel: View
    private var postSaveAsAction: Runnable? = null
    private var keyboardShown = false
    private var dataInspectorShown = true
    private var lastBackKeyPressTime = -1L
    private var lastReleaseBackKeyPressTime = -1L
    private val basicValuesPositionColorModifier = BasicValuesPositionColorModifier()
    private var fallbackFileType = FallbackFileType.FILE

    private val searchStatusListener = object : BinarySearchService.SearchStatusListener {
        override fun setStatus(foundMatches: BinarySearchService.FoundMatches, matchMode: SearchParameters.MatchMode) {
            runOnUiThread {
                showSearchStatusPanel()
                updateSearchStatusPanel(foundMatches.matchPosition, foundMatches.matchesCount)
            }
        }

        override fun clearStatus() {
            runOnUiThread {
                hideSearchStatusPanel()
            }
        }
    }

    private val codeAreaChangeListener = BinaryDataUndoRedoChangeListener {
        menu?.let { updateUndoState() }
    }

    private val codeAreaDataChangedListener = DataChangedListener {
        updateCurrentDocumentSize()
        val application = getApplication() as ApplicationContext
        if (application.isSearchActive()) {
            binarySearch.cancelSearch()
            binarySearch.clearSearch()
            hideSearchStatusPanel()
        }
    }

    private val codeAreaSelectionChangedListener = SelectionChangedListener {
        binaryStatus.setSelectionRange(codeArea.selection)
    }

    private val codeAreaCodeAreaCaretListener = CodeAreaCaretListener { caretPosition ->
        binaryStatus.setCursorPosition(caretPosition)

        val showKeyboard = codeArea.activeSection == BasicCodeAreaSection.TEXT_PREVIEW
        if (showKeyboard != keyboardShown) {
            keyboardShown = showKeyboard
            codeArea.requestFocus()
            codeArea.postDelayed({
                val im = getApplication().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                if (showKeyboard) {
                    im.showSoftInput(codeArea, InputMethodManager.SHOW_IMPLICIT)
                } else {
                    im.hideSoftInputFromWindow(codeArea.windowToken, 0)
                }
            }, 100)
        }
    }

    private val codeAreaOnKeyListener = CodeAreaKeyListener()
    private var codeAreaOnUnhandledKeyListener: Any? = null
    private val codeAreaEditModeChangedListener = EditModeChangedListener { editMode, editOperation ->
        binaryStatus.setEditMode(editMode, editOperation)
    }

    private val openFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        openFileResultCallback(result)
    }
    private val openTableFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        openTableFileResultCallback(result)
    }
    private val saveFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        saveFileResultCallback(result)
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        settingsResultCallback(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            CompatUtils.enableEdgeToEdge(this)
            window.decorView.setPadding(0, 0, 0, navigationBarHeight)
        }

        super.onCreate(savedInstanceState)

        val application = getApplication() as ApplicationContext
        appPreferences = application.getAppPreferences()
        setContentView(R.layout.activity_main)

        val mainPreferences = appPreferences.mainPreferences
        val theme = mainPreferences.theme
        when {
            Theme.DARK.name.equals(theme, ignoreCase = true) -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            Theme.LIGHT.name.equals(theme, ignoreCase = true) -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        toolbar = findViewById(R.id.toolbar)
        keyPanel = findViewById(R.id.keyPanel)
        val inflater = layoutInflater
        searchStatusPanel = inflater.inflate(R.layout.search_panel, null)
        basicValuesInspectorView = findViewById(R.id.basic_values_inspector)
        setSupportActionBar(toolbar)

        // For now steal code area and keep it in application context
        val existingFileHandler = application.getFileHandler()
        basicValuesInspector = BasicValuesInspector()
        if (existingFileHandler == null) {
            codeArea = findViewById(R.id.codeArea)
            fileHandler = application.createFileHandler(codeArea)
        } else {
            fileHandler = existingFileHandler
            codeArea = fileHandler.codeArea
            val parentView = codeArea.parent as ViewGroup
            parentView.removeView(codeArea)
            val contentView = findViewById<ViewGroup>(R.id.contentMain)
            contentView.removeView(findViewById(R.id.codeArea))
            contentView.addView(codeArea)
        }

        CompatUtils.setApplicationLocales(this, CompatUtils.getApplicationLocales(this))

        val basicColors = codeArea.basicColors.orElse(null)
            ?: throw IllegalStateException("Missing colors profile")
        basicColors.setContext(this)
        basicColors.reinitialize()
        codeArea.resetColors()

        binarySearch = BinarySearch()
        binarySearch.binarySearchService = BinarySearchServiceImpl(codeArea)

        registerForContextMenu(codeArea)

        fileHandler.undoRedo.addChangeListener(codeAreaChangeListener)
        codeArea.addDataChangedListener(codeAreaDataChangedListener)
        codeArea.addSelectionChangedListener(codeAreaSelectionChangedListener)
        codeArea.addCaretMovedListener(codeAreaCodeAreaCaretListener)
        codeArea.addEditModeChangedListener(codeAreaEditModeChangedListener)
        codeArea.setOnKeyListener(codeAreaOnKeyListener)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            codeAreaOnUnhandledKeyListener = View.OnUnhandledKeyEventListener { view, event ->
                codeAreaOnKeyListener.onKey(view, KeyEvent.KEYCODE_UNKNOWN, event)
            }
            codeArea.addOnUnhandledKeyEventListener(codeAreaOnUnhandledKeyListener as View.OnUnhandledKeyEventListener)
        }
        
        basicValuesInspector.setCodeArea(codeArea, fileHandler.undoRedo, basicValuesInspectorView)
        basicValuesInspector.enableUpdate()
        val codeAreaAssessor = fileHandler.codeAreaAssessor
        codeAreaAssessor.addColorModifier(basicValuesPositionColorModifier)
        basicValuesInspector.registerFocusPainter(basicValuesPositionColorModifier)

        basicValuesInspectorView.nextFocusUpId = R.id.toolbar
        if (application.isSearchActive()) {
            showSearchStatusPanel()
            val searchAssessor = CodeAreaAndroidUtils.findColorAssessor(
                codeArea.painter as ColorAssessorPainterCapable,
                SearchCodeAreaColorAssessor::class.java
            )
            searchAssessor?.let {
                updateSearchStatusPanel(it.currentMatchIndex, it.matches.size)
            }
        }

        applySettings()
        processIntent(intent)
        codeArea.post { codeArea.requestFocus() }
    }

    private fun setupKeyPanel(keysPanelMode: KeysPanelMode) {
        val mainView = findViewById<LinearLayout>(R.id.main)
        val keyPanelIndex = mainView.indexOfChild(keyPanel)
        
        if (keysPanelMode == KeysPanelMode.HIDE) {
            if (keyPanelIndex >= 0) {
                mainView.removeViewAt(keyPanelIndex)
                mainView.requestLayout()
            }
            return
        }

        if (keyPanelIndex == -1) {
            val searchStatusPanelIndex = mainView.indexOfChild(searchStatusPanel)
            if (searchStatusPanelIndex >= 0) {
                mainView.addView(keyPanel, 2)
            } else {
                mainView.addView(keyPanel, searchStatusPanelIndex + 1)
            }
            mainView.requestLayout()
        }

        when (keysPanelMode) {
            KeysPanelMode.SMALL -> setupKeyPanelSize(60, 40)
            KeysPanelMode.MEDIUM -> setupKeyPanelSize(90, 60)
            KeysPanelMode.BIG -> setupKeyPanelSize(120, 80)
            else -> {}
        }
        keyPanel.requestLayout()
    }

    private fun setupKeyPanelSize(buttonWidth: Int, buttonHeight: Int) {
        val buttons = listOf(
            R.id.button0, R.id.button1, R.id.button2, R.id.button3, R.id.button4,
            R.id.button5, R.id.button6, R.id.button7, R.id.button8, R.id.button9,
            R.id.buttonA, R.id.buttonB, R.id.buttonC, R.id.buttonD, R.id.buttonE, R.id.buttonF,
            R.id.buttonHome, R.id.buttonEnd, R.id.buttonLeft, R.id.buttonRight,
            R.id.buttonUp, R.id.buttonDown, R.id.buttonDelete, R.id.buttonBk,
            R.id.buttonInsert, R.id.buttonTab
        )
        
        buttons.forEach { buttonId ->
            findViewById<Button>(buttonId).apply {
                minWidth = buttonWidth
                minHeight = buttonHeight
            }
        }
    }

    private fun showSearchStatusPanel() {
        val mainView = findViewById<LinearLayout>(R.id.main)
        val searchStatusPanelIndex = mainView.indexOfChild(searchStatusPanel)
        
        if (searchStatusPanelIndex == -1) {
            val searchStatus = searchStatusPanel.findViewById<TextView>(R.id.searchStatus)
            val prevButton = searchStatusPanel.findViewById<Button>(R.id.previousMatchButton)
            prevButton.isEnabled = false
            val nextButton = searchStatusPanel.findViewById<Button>(R.id.nextMatchButton)
            nextButton.isEnabled = false

            searchStatus.text = resources.getString(R.string.search_in_progress)
            mainView.addView(searchStatusPanel, 2)
            
            val application = getApplication() as ApplicationContext
            application.setSearchActive(true)
        }
    }

    private fun hideSearchStatusPanel() {
        val mainView = findViewById<LinearLayout>(R.id.main)
        val searchStatusPanelIndex = mainView.indexOfChild(searchStatusPanel)
        
        if (searchStatusPanelIndex >= 0) {
            mainView.removeViewAt(searchStatusPanelIndex)
            val application = getApplication() as ApplicationContext
            application.setSearchActive(false)
        }
    }

    private fun updateSearchStatusPanel(matchPosition: Int, matchesCount: Int) {
        val searchStatus = searchStatusPanel.findViewById<TextView>(R.id.searchStatus)
        val prevButton = searchStatusPanel.findViewById<Button>(R.id.previousMatchButton)
        prevButton.isEnabled = matchPosition > 0 && matchesCount > 0
        val nextButton = searchStatusPanel.findViewById<Button>(R.id.nextMatchButton)
        nextButton.isEnabled = matchPosition < matchesCount - 1 && matchesCount > 0

        val resources = resources
        searchStatus.text = when {
            matchesCount == 1 -> resources.getString(R.string.search_match_single)
            matchesCount > 0 -> String.format(resources.getString(R.string.search_match_found), matchPosition + 1, matchesCount)
            else -> resources.getString(R.string.search_match_none)
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            codeArea.removeOnUnhandledKeyEventListener(codeAreaOnUnhandledKeyListener as View.OnUnhandledKeyEventListener)
        }
        codeArea.setOnKeyListener(null)
        codeArea.removeEditModeChangedListener(codeAreaEditModeChangedListener)
        codeArea.removeCaretMovedListener(codeAreaCodeAreaCaretListener)
        codeArea.removeSelectionChangedListener(codeAreaSelectionChangedListener)
        codeArea.removeDataChangedListener(codeAreaDataChangedListener)
        fileHandler.undoRedo.removeChangeListener(codeAreaChangeListener)
        val codeAreaAssessor = fileHandler.codeAreaAssessor
        codeAreaAssessor.removeColorModifier(basicValuesPositionColorModifier)

        super.onDestroy()
    }

    private fun processIntent(intent: Intent) {
        val action = intent.action ?: return

        if (Intent.ACTION_VIEW == action || Intent.ACTION_EDIT == action) {
            val scheme = intent.scheme
            if (ContentResolver.SCHEME_FILE == scheme || ContentResolver.SCHEME_CONTENT == scheme) {
                val fileUri = intent.data
                if (fileUri != null) {
                    releaseFile {
                        try {
                            fileHandler.openFile(contentResolver, fileUri, appPreferences.editorPreferences.fileHandlingMode)
                            // Content should be opened as unspecified file
                            if (ContentResolver.SCHEME_CONTENT == scheme) {
                                fileHandler.clearFileUri()
                            }
                            updateStatus()
                        } catch (tw: Throwable) {
                            reportException(tw)
                        }
                    }
                }
            }
        }
    }

    private fun getLanguageLocaleList(): LocaleListCompat {
        val language = appPreferences.mainPreferences.localeTag
        return if (language.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language)
        }
    }

    private fun applySettings() {
        appPreferences.codeAreaPreferences.applyPreferences(codeArea)
        try {
            codeArea.charset = Charset.forName(appPreferences.encodingPreferences.defaultEncoding)
        } catch (ex: Exception) {
            Logger.getLogger(MainActivity::class.java.name).log(Level.SEVERE, null, ex)
        }

        try {
            // TODO: Fix code font
            val codeFont = Font() // codeArea.getCodeFont();
            val fontSize = appPreferences.fontPreferences.fontSize
            codeFont.size = fontSize
            codeArea.codeFont = codeFont
        } catch (ex: Exception) {
            Logger.getLogger(MainActivity::class.java.name).log(Level.SEVERE, null, ex)
        }
        binaryStatus.setEncoding(codeArea.charset.name())

        val editorPreferences = appPreferences.editorPreferences
        setupKeyPanel(editorPreferences.keysPanelMode)
        val dataInspectorMode = editorPreferences.dataInspectorMode
        val showDataInspector = dataInspectorMode == DataInspectorMode.SHOW || 
            (dataInspectorMode == DataInspectorMode.LANDSCAPE && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        
        if (showDataInspector != dataInspectorShown) {
            val mainHorizontalLayout = findViewById<LinearLayout>(R.id.mainHorizontalLayout)
            if (showDataInspector) {
                basicValuesInspector.enableUpdate()
                mainHorizontalLayout.addView(basicValuesInspectorView)
            } else {
                basicValuesInspector.disableUpdate()
                mainHorizontalLayout.removeView(basicValuesInspectorView)
            }
            mainHorizontalLayout.requestLayout()
            dataInspectorShown = showDataInspector
        }

        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_YES) > 0
        basicValuesPositionColorModifier.setDarkMode(isDarkMode)

        updateStatus()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)

        // If possible, attempt to show icons in the main menu via reflection
        if (menu.javaClass.simpleName == "MenuBuilder") {
            try {
                val method = menu.javaClass.getDeclaredMethod("setOptionalIconsVisible", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(menu, true)
            } catch (ex: NoSuchMethodException) {
                Logger.getLogger(MainActivity::class.java.name).log(Level.SEVERE, null, ex)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        this.menu = menu

        // Currently on Google TV access to app bar icons doesn't seem to work
        if (CompatUtils.isAndroidTV(this)) {
            for (i in 0 until menu.size()) {
                val menuItem = menu.getItem(i)
                menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            }
        }

        codeArea.addSelectionChangedListener { updateEditActionsState() }
        updateUndoState()
        updateEditActionsState()
        updateViewActionsState()

        return true
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val resources = resources
        var order = 0
        
        if (CompatUtils.isAndroidTV(this)) {
            menu.add(0, GO_TO_SIDE_PANEL_POPUP_ID, order, resources.getString(R.string.action_go_to_side_panel))
            order++
            menu.add(0, OPEN_MAIN_MENU_POPUP_ID, order, resources.getString(R.string.action_open_main_menu))
            order++
            if (codeArea.codeAreaCaret.section == BasicCodeAreaSection.TEXT_PREVIEW) {
                menu.add(0, SHOW_KEYBOARD_MENU_POPUP_ID, order, resources.getString(R.string.action_show_keyboard))
                order++
            }
        }
        
        menu.add(0, SELECTION_START_POPUP_ID, order, resources.getString(R.string.action_selection_start))
        menu.add(0, SELECTION_END_POPUP_ID, order + 1, resources.getString(R.string.action_selection_end))
        menu.add(0, CLEAR_SELECTION_POPUP_ID, order + 2, resources.getString(R.string.action_clear_selection))
        
        val cutMenuItem = menu.add(1, CUT_ACTION_POPUP_ID, order + 3, resources.getString(R.string.action_cut))
        cutMenuItem.isEnabled = codeArea.isEditable && codeArea.hasSelection()
        
        val copyMenuItem = menu.add(1, COPY_ACTION_POPUP_ID, order + 4, resources.getString(R.string.action_copy))
        copyMenuItem.isEnabled = codeArea.hasSelection()
        
        menu.add(1, COPY_AS_CODE_ACTION_POPUP_ID, order + 5, resources.getString(R.string.action_copy_as_code))
        
        val pasteMenuItem = menu.add(1, PASTE_ACTION_POPUP_ID, order + 6, resources.getString(R.string.action_paste))
        pasteMenuItem.isEnabled = codeArea.isEditable && codeArea.canPaste()
        
        menu.add(1, PASTE_FROM_CODE_ACTION_POPUP_ID, order + 7, resources.getString(R.string.action_paste_from_code))
        
        val deleteMenuItem = menu.add(1, DELETE_ACTION_POPUP_ID, order + 8, resources.getString(R.string.action_delete))
        deleteMenuItem.isEnabled = codeArea.isEditable && codeArea.hasSelection()
        
        menu.add(1, SELECT_ALL_ACTION_POPUP_ID, order + 9, resources.getString(R.string.action_select_all))
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            GO_TO_SIDE_PANEL_POPUP_ID -> {
                val mainView = findViewById<LinearLayout>(R.id.main)
                val searchStatusPanelIndex = mainView.indexOfChild(searchStatusPanel)
                if (searchStatusPanelIndex >= 0) {
                    val closeButton = findViewById<View>(R.id.closeButton)
                    closeButton.requestFocus()
                    return true
                }
                val keyPanelIndex = mainView.indexOfChild(keyPanel)
                if (keyPanelIndex >= 0) {
                    val downButton = findViewById<View>(R.id.buttonDown)
                    downButton.requestFocus()
                    return true
                }

                val editTextByte = findViewById<View>(R.id.editTextByte)
                editTextByte.requestFocus()
            }
            OPEN_MAIN_MENU_POPUP_ID -> {
                toolbar.showOverflowMenu()
            }
            SHOW_KEYBOARD_MENU_POPUP_ID -> {
                // For some reason it keeps closing if invoked immediately
                codeArea.requestFocus()
                codeArea.postDelayed({
                    val im = getApplication().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    im.showSoftInput(codeArea, InputMethodManager.SHOW_IMPLICIT)
                }, 100)
            }
            SELECTION_START_POPUP_ID -> {
                val selection = codeArea.selection
                val touchCaretPosition = codeArea.mousePositionToClosestCaretPosition(
                    codeArea.touchPositionX.toInt(),
                    codeArea.touchPositionY.toInt(),
                    CaretOverlapMode.PARTIAL_OVERLAP
                )
                if (selection.isEmpty) {
                    codeArea.setSelection(touchCaretPosition.dataPosition, codeArea.dataPosition)
                } else {
                    codeArea.setSelection(touchCaretPosition.dataPosition, selection.end)
                }
            }
            SELECTION_END_POPUP_ID -> {
                val selection = codeArea.selection
                val touchCaretPosition = codeArea.mousePositionToClosestCaretPosition(
                    codeArea.touchPositionX.toInt(),
                    codeArea.touchPositionY.toInt(),
                    CaretOverlapMode.PARTIAL_OVERLAP
                )
                if (selection.isEmpty) {
                    codeArea.setSelection(codeArea.dataPosition, touchCaretPosition.dataPosition)
                } else {
                    codeArea.setSelection(selection.start, touchCaretPosition.dataPosition)
                }
            }
            CLEAR_SELECTION_POPUP_ID -> {
                codeArea.clearSelection()
            }
            CUT_ACTION_POPUP_ID -> {
                try {
                    codeArea.cut()
                } catch (tw: Throwable) {
                    reportException(tw)
                }
            }
            COPY_ACTION_POPUP_ID -> {
                try {
                    codeArea.copy()
                } catch (tw: Throwable) {
                    reportException(tw)
                }
            }
            PASTE_ACTION_POPUP_ID -> {
                try {
                    codeArea.paste()
                } catch (tw: Throwable) {
                    reportException(tw)
                }
            }
            DELETE_ACTION_POPUP_ID -> {
                codeArea.delete()
            }
            SELECT_ALL_ACTION_POPUP_ID -> {
                codeArea.selectAll()
            }
            COPY_AS_CODE_ACTION_POPUP_ID -> {
                try {
                    (codeArea.commandHandler as CodeAreaOperationCommandHandler).copyAsCode()
                } catch (tw: Throwable) {
                    reportException(tw)
                }
            }
            PASTE_FROM_CODE_ACTION_POPUP_ID -> {
                try {
                    (codeArea.commandHandler as CodeAreaOperationCommandHandler).pasteFromCode()
                } catch (tw: Throwable) {
                    reportException(tw)
                }
            }
            else -> return false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        when (id) {
            R.id.action_new -> {
                releaseFile {
                    fileHandler.setNewData(appPreferences.editorPreferences.fileHandlingMode)
                }
                return true
            }
            R.id.action_open -> {
                releaseFile { openFile() }
                return true
            }
            R.id.action_open_table_file -> {
                openTableFile()
                return true
            }
            R.id.action_save -> {
                val currentFileUri = fileHandler.currentFileUri
                if (currentFileUri == null) {
                    saveAs()
                } else {
                    try {
                        fileHandler.saveFile(contentResolver, currentFileUri)
                    } catch (tw: Throwable) {
                        reportException(tw)
                        return false
                    }
                }
                return true
            }
            R.id.action_save_as -> {
                saveAs()
                return true
            }
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                settingsLauncher.launch(intent)
                return true
            }
            R.id.action_about -> {
                val manager = packageManager
                var appVersion = ""
                try {
                    val info = manager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
                    appVersion = info.versionName ?: ""
                } catch (e: PackageManager.NameNotFoundException) {
                    // ignore
                }

                val aboutDialog = AboutDialog()
                aboutDialog.setAppVersion(appVersion)
                aboutDialog.show(supportFragmentManager, "aboutDialog")
                return true
            }
            R.id.action_exit -> {
                releaseFile {
                    finish()
                    System.exit(0)
                }
                return true
            }
            R.id.code_type -> {
                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.code_type)
                builder.setSingleChoiceItems(resources.getTextArray(R.array.code_type_entries), codeArea.codeType.ordinal) { dialog, which ->
                    val codeType = CodeType.values()[which]
                    codeArea.codeType = codeType
                    appPreferences.codeAreaPreferences.codeType = codeType
                    dialog.dismiss()
                }
                builder.setNegativeButton(R.string.button_cancel, null)
                val alertDialog = builder.create()
                alertDialog.show()
                return true
            }
            R.id.view_mode -> {
                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.view_mode)
                builder.setSingleChoiceItems(resources.getTextArray(R.array.view_mode_entries), codeArea.viewMode.ordinal) { dialog, which ->
                    val viewMode = CodeAreaViewMode.values()[which]
                    codeArea.viewMode = viewMode
                    appPreferences.codeAreaPreferences.viewMode = viewMode
                    dialog.dismiss()
                }
                builder.setNegativeButton(R.string.button_cancel, null)
                val alertDialog = builder.create()
                alertDialog.show()
                return true
            }
            R.id.hex_chars_case -> {
                val builder = AlertDialog.Builder(this)
                builder.setTitle(R.string.hex_characters_case)
                builder.setSingleChoiceItems(resources.getTextArray(R.array.hex_chars_case_entries), codeArea.codeCharactersCase.ordinal) { dialog, which ->
                    val codeCharactersCase = CodeCharactersCase.values()[which]
                    codeArea.codeCharactersCase = codeCharactersCase
                    appPreferences.codeAreaPreferences.codeCharactersCase = codeCharactersCase
                    dialog.dismiss()
                }
                builder.setNegativeButton(R.string.button_cancel, null)
                val alertDialog = builder.create()
                alertDialog.show()
                return true
            }
            R.id.encoding -> {
                EncodingPreference.showEncodingSelectionDialog(this, codeArea.charset.name()) { encoding ->
                    codeArea.charset = Charset.forName(encoding)
                    appPreferences.encodingPreferences.defaultEncoding = encoding
                    binaryStatus.setEncoding(codeArea.charset.name())
                }
                return true
            }
            R.id.font -> {
                FontPreference.showFontSelectionDialog(this, codeArea.codeFont) { codeFont ->
                    codeArea.codeFont = codeFont
                    appPreferences.fontPreferences.fontSize = codeFont.size
                }
                return true
            }
            R.id.bytes_per_row_fill -> {
                codeArea.rowWrapping = RowWrappingMode.WRAPPING
                codeArea.maxBytesPerRow = 0
                appPreferences.codeAreaPreferences.rowWrappingMode = codeArea.rowWrapping
                appPreferences.codeAreaPreferences.maxBytesPerRow = codeArea.maxBytesPerRow
                menu?.findItem(R.id.bytes_per_row_fill)?.isChecked = true
                return true
            }
            R.id.bytes_per_row_4 -> {
                codeArea.rowWrapping = RowWrappingMode.NO_WRAPPING
                codeArea.maxBytesPerRow = 4
                appPreferences.codeAreaPreferences.rowWrappingMode = codeArea.rowWrapping
                appPreferences.codeAreaPreferences.maxBytesPerRow = codeArea.maxBytesPerRow
                menu?.findItem(R.id.bytes_per_row_4)?.isChecked = true
                return true
            }
            R.id.bytes_per_row_8 -> {
                codeArea.rowWrapping = RowWrappingMode.NO_WRAPPING
                codeArea.maxBytesPerRow = 8
                appPreferences.codeAreaPreferences.rowWrappingMode = codeArea.rowWrapping
                appPreferences.codeAreaPreferences.maxBytesPerRow = codeArea.maxBytesPerRow
                menu?.findItem(R.id.bytes_per_row_8)?.isChecked = true
                return true
            }
            R.id.bytes_per_row_12 -> {
                codeArea.rowWrapping = RowWrappingMode.NO_WRAPPING
                codeArea.maxBytesPerRow = 12
                appPreferences.codeAreaPreferences.rowWrappingMode = codeArea.rowWrapping
                appPreferences.codeAreaPreferences.maxBytesPerRow = codeArea.maxBytesPerRow
                menu?.findItem(R.id.bytes_per_row_12)?.isChecked = true
                return true
            }
            R.id.bytes_per_row_16 -> {
                codeArea.rowWrapping = RowWrappingMode.NO_WRAPPING
                codeArea.maxBytesPerRow = 16
                appPreferences.codeAreaPreferences.rowWrappingMode = codeArea.rowWrapping
                appPreferences.codeAreaPreferences.maxBytesPerRow = codeArea.maxBytesPerRow
                menu?.findItem(R.id.bytes_per_row_16)?.isChecked = true
                return true
            }
            R.id.non_printable_characters -> {
                val checked = item.isChecked
                item.isChecked = !checked
                val nonprintablesCodeAreaAssessor = CodeAreaAndroidUtils.findColorAssessor(
                    codeArea.painter as ColorAssessorPainterCapable,
                    NonprintablesCodeAreaAssessor::class.java
                )
                nonprintablesCodeAreaAssessor?.setShowNonprintables(!checked)
                appPreferences.codeAreaPreferences.isShowNonprintables = !checked
                return true
            }
            R.id.code_colorization -> {
                val checked = item.isChecked
                item.isChecked = !checked
                val nonAsciiColorAssessor = CodeAreaAndroidUtils.findColorAssessor(
                    codeArea.painter as ColorAssessorPainterCapable,
                    NonAsciiCodeAreaColorAssessor::class.java
                )
                nonAsciiColorAssessor?.isNonAsciiHighlightingEnabled = !checked
                appPreferences.codeAreaPreferences.isCodeColorization = !checked
                return true
            }
            R.id.action_undo -> {
                try {
                    fileHandler.undoRedo.performUndo()
                } catch (tw: Throwable) {
                    reportException(tw)
                }
                // TODO fix operations instead of validating
                codeArea.validateCaret()
                return true
            }
            R.id.action_redo -> {
                try {
                    fileHandler.undoRedo.performRedo()
                } catch (tw: Throwable) {
                    reportException(tw)
                }
                codeArea.validateCaret()
                return true
            }
            R.id.action_cut -> {
                try {
                    codeArea.cut()
                } catch (tw: Throwable) {
                    reportException(tw)
                }
                return true
            }
            R.id.action_copy -> {
                try {
                    codeArea.copy()
                } catch (tw: Throwable) {
                    reportException(tw)
                }
                return true
            }
            R.id.action_paste -> {
                try {
                    codeArea.paste()
                } catch (tw: Throwable) {
                    reportException(tw)
                }
                return true
            }
            R.id.action_search -> {
                val searchDialog = SearchDialog()
                searchDialog.show(supportFragmentManager, "searchDialog")
                return true
            }
            R.id.action_delete -> {
                codeArea.delete()
                return true
            }
            R.id.action_select_all -> {
                codeArea.selectAll()
                return true
            }
            R.id.go_to_position -> {
                val goToPositionDialog = GoToPositionDialog()
                goToPositionDialog.setPositiveListener { _, _ ->
                    try {
                        val caretPosition = DefaultCodeAreaCaretPosition()
                        caretPosition.codeOffset = 0
                        caretPosition.dataPosition = minOf(goToPositionDialog.targetPosition, codeArea.dataSize)
                        codeArea.activeCaretPosition = caretPosition
                        codeArea.validateCaret()
                        codeArea.centerOnCursor()
                    } catch (ex: NumberFormatException) {
                        reportException(ex)
                    }
                }
                goToPositionDialog.show(supportFragmentManager, "goToPositionDialog")
                return true
            }
            R.id.edit_selection -> {
                val editSelectionDialog = EditSelectionDialog()
                editSelectionDialog.setPositiveListener { _, _ ->
                    try {
                        val selectionRange = editSelectionDialog.selectionRange
                        if (selectionRange.isPresent) {
                            codeArea.selection = selectionRange.get()
                        } else {
                            codeArea.clearSelection()
                        }
                        codeArea.revealCursor()
                    } catch (ex: NumberFormatException) {
                        reportException(ex)
                    }
                }
                editSelectionDialog.show(supportFragmentManager, "editSelectionDialog")
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }    fun 
releaseFile(postReleaseAction: Runnable) {
        if (!fileHandler.isModified) {
            postReleaseAction.run()
            return
        }

        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.file_modified)
        builder.setPositiveButton(R.string.button_save) { _, _ ->
            val currentFileUri = fileHandler.currentFileUri
            if (currentFileUri == null) {
                saveAs()
            } else {
                try {
                    fileHandler.saveFile(contentResolver, currentFileUri)
                } catch (tw: Throwable) {
                    reportException(tw)
                }
            }
            postReleaseAction.run()
        }
        builder.setNeutralButton(R.string.button_discard) { _, _ ->
            postReleaseAction.run()
        }
        builder.setNegativeButton(R.string.button_cancel, null)
        builder.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                if (System.currentTimeMillis() - lastReleaseBackKeyPressTime < DOUBLE_BACK_KEY_INTERVAL) {
                    postReleaseAction.run()
                } else {
                    lastReleaseBackKeyPressTime = System.currentTimeMillis()
                    Toast.makeText(this@MainActivity, resources.getText(R.string.confirm_discard), Toast.LENGTH_SHORT).show()
                }
                return@setOnKeyListener true
            }
            false
        }
        val alertDialog = builder.create()
        alertDialog.show()
    }

    fun openFile() {
        if (CompatUtils.isAndroidTV(this) || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            fallBackOpenFile(FallbackFileType.FILE)
            return
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.putExtra(Intent.EXTRA_LOCALE_LIST, getLanguageLocaleList().unwrap() as android.os.Parcelable)
        }

        val pickerInitialUri = fileHandler.pickerInitialUri
        if (pickerInitialUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }

        try {
            openFileLauncher.launch(Intent.createChooser(intent, resources.getString(R.string.select_file)))
        } catch (ex: ActivityNotFoundException) {
            fallBackOpenFile(FallbackFileType.FILE)
        }
    }

    fun openTableFile() {
        if (CompatUtils.isAndroidTV(this) || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            fallBackOpenFile(FallbackFileType.TABLE_FILE)
            return
        }

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.putExtra(Intent.EXTRA_LOCALE_LIST, getLanguageLocaleList().unwrap() as android.os.Parcelable)
        }

        try {
            openTableFileLauncher.launch(Intent.createChooser(intent, resources.getString(R.string.select_file)))
        } catch (ex: ActivityNotFoundException) {
            fallBackOpenFile(FallbackFileType.TABLE_FILE)
        }
    }

    private fun fallBackOpenFile(fallbackFileType: FallbackFileType) {
        this.fallbackFileType = fallbackFileType
        val permissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            requestWriteExternalStoragePermission()
            return
        }

        val dialog = OpenFileDialog()
        dialog.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.AppTheme)
        dialog.show(supportFragmentManager, OpenFileDialog::class.java.name)
    }

    fun saveAs() {
        saveAs(null)
    }

    fun saveAs(postSaveAsAction: Runnable?) {
        this.postSaveAsAction = postSaveAsAction

        if (CompatUtils.isAndroidTV(this) || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            fallBackSaveAs()
            return
        }

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.putExtra(Intent.EXTRA_LOCALE_LIST, getLanguageLocaleList().unwrap() as android.os.Parcelable)
        }

        val pickerInitialUri = fileHandler.pickerInitialUri
        if (pickerInitialUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }

        try {
            saveFileLauncher.launch(Intent.createChooser(intent, resources.getString(R.string.save_as_file)))
        } catch (ex: ActivityNotFoundException) {
            fallBackSaveAs()
        } catch (tw: Throwable) {
            reportException(tw)
        }
    }

    private fun fallBackSaveAs() {
        val permissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) {
            val dialog = SaveFileDialog()
            dialog.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.AppTheme)
            dialog.show(supportFragmentManager, SaveFileDialog::class.java.name)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val permissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) {
            Toast.makeText(this, R.string.storage_permission_is_not_granted, Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        processIntent(intent)
    }

    fun updateStatus() {
        updateCurrentDocumentSize()
        updateCurrentCaretPosition()
        updateCurrentSelectionRange()
        updateCurrentMemoryMode()
        updateCurrentEditMode()
    }

    private fun updateCurrentDocumentSize() {
        val dataSize = codeArea.dataSize
        binaryStatus.setCurrentDocumentSize(dataSize, fileHandler.documentOriginalSize)
    }

    private fun updateCurrentCaretPosition() {
        val caretPosition = codeArea.activeCaretPosition
        binaryStatus.setCursorPosition(caretPosition)
    }

    private fun updateCurrentSelectionRange() {
        val selectionRange = codeArea.selection
        binaryStatus.setSelectionRange(selectionRange)
    }

    private fun updateCurrentMemoryMode() {
        var newMemoryMode = BinaryStatusApi.MemoryMode.RAM_MEMORY
        when {
            codeArea.editMode == EditMode.READ_ONLY -> {
                newMemoryMode = BinaryStatusApi.MemoryMode.READ_ONLY
            }
            codeArea.contentData is DeltaDocument -> {
                newMemoryMode = BinaryStatusApi.MemoryMode.DELTA_MODE
            }
        }
        binaryStatus.setMemoryMode(newMemoryMode)
    }

    private fun updateCurrentEditMode() {
        binaryStatus.setEditMode(codeArea.editMode, codeArea.activeOperation)
    }

    private fun updateEditActionsState() {
        val menu = this.menu ?: return
        
        val cutMenuItem = menu.findItem(R.id.action_cut)
        cutMenuItem.isEnabled = codeArea.isEditable && codeArea.hasSelection()

        val copyMenuItem = menu.findItem(R.id.action_copy)
        copyMenuItem.isEnabled = codeArea.hasSelection()
        
        val pasteMenuItem = menu.findItem(R.id.action_paste)
        pasteMenuItem.isEnabled = codeArea.isEditable && codeArea.canPaste()
        
        val deleteMenuItem = menu.findItem(R.id.action_delete)
        deleteMenuItem.isEnabled = codeArea.isEditable && codeArea.hasSelection()
    }

    private fun updateViewActionsState() {
        val menu = this.menu ?: return
        
        menu.findItem(R.id.code_colorization).isChecked = appPreferences.codeAreaPreferences.isCodeColorization
        menu.findItem(R.id.non_printable_characters).isChecked = appPreferences.codeAreaPreferences.isShowNonprintables
        
        val bytesPerRow = appPreferences.codeAreaPreferences.maxBytesPerRow
        when (bytesPerRow) {
            0 -> menu.findItem(R.id.bytes_per_row_fill).isChecked = true
            4 -> menu.findItem(R.id.bytes_per_row_4).isChecked = true
            8 -> menu.findItem(R.id.bytes_per_row_8).isChecked = true
            12 -> menu.findItem(R.id.bytes_per_row_12).isChecked = true
            16 -> menu.findItem(R.id.bytes_per_row_16).isChecked = true
        }
    }

    private fun updateUndoState() {
        val menu = this.menu ?: return
        
        val saveMenuItem = menu.findItem(R.id.action_save)
        val currentFileUri = fileHandler.currentFileUri
        saveMenuItem.isEnabled = currentFileUri == null || fileHandler.undoRedo.isModified

        val canUndo = fileHandler.undoRedo.canUndo()
        val undoMenuItem = menu.findItem(R.id.action_undo)
        undoMenuItem.isEnabled = canUndo

        val canRedo = fileHandler.undoRedo.canRedo()
        val redoMenuItem = menu.findItem(R.id.action_redo)
        redoMenuItem.isEnabled = canRedo
    } 
   private fun openFileResultCallback(activityResult: ActivityResult) {
        val resultCode = activityResult.resultCode
        val data = activityResult.data
        if (resultCode != RESULT_OK || data == null || data.data == null) {
            return
        }

        try {
            fileHandler.openFile(contentResolver, data.data!!, appPreferences.editorPreferences.fileHandlingMode)
            updateStatus()
        } catch (tw: Throwable) {
            reportException(tw)
        }
    }

    private fun openTableFileResultCallback(activityResult: ActivityResult) {
        val codeAreaTableMapAssessor = fileHandler.codeAreaTableMapAssessor
        val resultCode = activityResult.resultCode
        val data = activityResult.data
        if (resultCode != RESULT_OK || data == null || data.data == null) {
            codeAreaTableMapAssessor.setUseTable(false)
            return
        }

        try {
            codeAreaTableMapAssessor.openFile(contentResolver, data.data!!)
            fileHandler.codeArea.repaint()
        } catch (tw: Throwable) {
            reportException(tw)
        }
    }

    private fun saveFileResultCallback(activityResult: ActivityResult) {
        val resultCode = activityResult.resultCode
        val data = activityResult.data
        if (resultCode != RESULT_OK || data == null || data.data == null) {
            return
        }

        try {
            fileHandler.saveFile(contentResolver, data.data!!)
            postSaveAsAction?.run()
            postSaveAsAction = null
        } catch (tw: Throwable) {
            reportException(tw)
        }
    }

    /**
     * Legacy support for file dialog using external library.
     */
    override fun onFileSelected(dialog: FileDialog, file: File) {
        if (dialog is OpenFileDialog) {
            when (fallbackFileType) {
                FallbackFileType.FILE -> {
                    try {
                        fileHandler.openFile(contentResolver, Uri.fromFile(file), appPreferences.editorPreferences.fileHandlingMode)
                        updateStatus()
                    } catch (tw: Throwable) {
                        reportException(tw)
                    }
                }
                FallbackFileType.TABLE_FILE -> {
                    try {
                        fileHandler.codeAreaTableMapAssessor.openFile(contentResolver, Uri.fromFile(file))
                        fileHandler.codeArea.repaint()
                    } catch (tw: Throwable) {
                        reportException(tw)
                    }
                }
            }
        } else {
            try {
                fileHandler.saveFile(contentResolver, Uri.fromFile(file))
                postSaveAsAction?.run()
                postSaveAsAction = null
            } catch (tw: Throwable) {
                reportException(tw)
            }
        }
    }

    private fun settingsResultCallback(activityResult: ActivityResult) {
        applySettings()
        menu?.let { updateViewActionsState() }
    }

    // Button action methods
    fun buttonAction0(view: View) {
        codeArea.commandHandler.keyTyped('0'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0))
    }

    fun buttonAction1(view: View) {
        codeArea.commandHandler.keyTyped('1'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1))
    }

    fun buttonAction2(view: View) {
        codeArea.commandHandler.keyTyped('2'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_2))
    }

    fun buttonAction3(view: View) {
        codeArea.commandHandler.keyTyped('3'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_3))
    }

    fun buttonAction4(view: View) {
        codeArea.commandHandler.keyTyped('4'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_4))
    }

    fun buttonAction5(view: View) {
        codeArea.commandHandler.keyTyped('5'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_5))
    }

    fun buttonAction6(view: View) {
        codeArea.commandHandler.keyTyped('6'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_6))
    }

    fun buttonAction7(view: View) {
        codeArea.commandHandler.keyTyped('7'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_7))
    }

    fun buttonAction8(view: View) {
        codeArea.commandHandler.keyTyped('8'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_8))
    }

    fun buttonAction9(view: View) {
        codeArea.commandHandler.keyTyped('9'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_9))
    }

    fun buttonActionA(view: View) {
        codeArea.commandHandler.keyTyped('a'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A))
    }

    fun buttonActionB(view: View) {
        codeArea.commandHandler.keyTyped('b'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_B))
    }

    fun buttonActionC(view: View) {
        codeArea.commandHandler.keyTyped('c'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_C))
    }

    fun buttonActionD(view: View) {
        codeArea.commandHandler.keyTyped('d'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_D))
    }

    fun buttonActionE(view: View) {
        codeArea.commandHandler.keyTyped('e'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_E))
    }

    fun buttonActionF(view: View) {
        codeArea.commandHandler.keyTyped('f'.code, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F))
    }

    fun buttonActionUp(view: View) {
        codeArea.commandHandler.keyPressed(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP))
    }

    fun buttonActionDown(view: View) {
        codeArea.commandHandler.keyPressed(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
    }

    fun buttonActionLeft(view: View) {
        codeArea.commandHandler.keyPressed(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
    }

    fun buttonActionRight(view: View) {
        codeArea.commandHandler.keyPressed(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    fun buttonActionHome(view: View) {
        codeArea.commandHandler.keyPressed(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_HOME))
    }

    fun buttonActionEnd(view: View) {
        codeArea.commandHandler.keyPressed(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MOVE_END))
    }

    fun buttonActionInsert(view: View) {
        codeArea.commandHandler.keyPressed(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_INSERT))
    }

    fun buttonActionDelete(view: View) {
        codeArea.commandHandler.keyPressed(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_FORWARD_DEL))
    }

    fun buttonActionBk(view: View) {
        codeArea.commandHandler.keyPressed(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
    }

    fun buttonActionTab(view: View) {
        codeArea.commandHandler.keyPressed(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_TAB))
    }

    fun buttonActionPreviousMatch(view: View) {
        val searchAssessor = CodeAreaAndroidUtils.findColorAssessor(
            codeArea.painter as ColorAssessorPainterCapable,
            SearchCodeAreaColorAssessor::class.java
        )
        searchAssessor?.let { assessor ->
            assessor.currentMatchIndex = assessor.currentMatchIndex - 1
            updateSearchStatusPanel(assessor.currentMatchIndex, assessor.matches.size)
            val currentMatch = assessor.currentMatch
            if (currentMatch != null) {
                codeArea.revealPosition(currentMatch.position, 0, codeArea.activeSection)
                codeArea.repaint()
            }
        }
    }

    fun buttonActionNextMatch(view: View) {
        val searchAssessor = CodeAreaAndroidUtils.findColorAssessor(
            codeArea.painter as ColorAssessorPainterCapable,
            SearchCodeAreaColorAssessor::class.java
        )
        searchAssessor?.let { assessor ->
            assessor.currentMatchIndex = assessor.currentMatchIndex + 1
            updateSearchStatusPanel(assessor.currentMatchIndex, assessor.matches.size)
            val currentMatch = assessor.currentMatch
            if (currentMatch != null) {
                codeArea.revealPosition(currentMatch.position, 0, codeArea.activeSection)
                codeArea.repaint()
            }
        }
    }

    fun buttonActionHideSearchPanel(view: View) {
        binarySearch.cancelSearch()
        binarySearch.clearSearch()
        hideSearchStatusPanel()
    }

    fun getBinarySearch(): BinarySearch? = binarySearch

    fun getSearchParameters(): SearchParameters? {
        val application = getApplication() as ApplicationContext
        return application.getSearchParameters()
    }

    fun setSearchParameters(searchParameters: SearchParameters) {
        val application = getApplication() as ApplicationContext
        application.setSearchParameters(searchParameters)
    }

    fun getSearchStatusListener(): BinarySearchService.SearchStatusListener? = searchStatusListener

    fun getCodeArea(): CodeArea? = codeArea

    private fun requestWriteExternalStoragePermission() {
        val permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            val builder = AlertDialog.Builder(this)
            builder.setMessage(R.string.storage_permission_request)
            builder.setPositiveButton(R.string.button_request) { _, _ ->
                ActivityCompat.requestPermissions(this@MainActivity, permissions, STORAGE_PERMISSION_CODE)
            }
            builder.setNegativeButton(R.string.button_cancel, null)
            builder.show()
        } else {
            ActivityCompat.requestPermissions(this, permissions, STORAGE_PERMISSION_CODE)
        }
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
                if (keyEvent.action == KeyEvent.ACTION_UP) {
                    if (keyEvent.keyCode == KeyEvent.KEYCODE_BACK && CompatUtils.isAndroidTV(this@MainActivity)) {
                        codeArea.post { codeArea.requestFocus() }
                        return true
                    }
                }
                val currentFocus = currentFocus
                return currentFocus?.dispatchKeyEvent(keyEvent) ?: false
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
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER -> {
                                if (keyEvent.eventTime - keyEvent.downTime > TimeUnit.SECONDS.toMillis(1)) {
                                    toolbar.showOverflowMenu()
                                } else {
                                    codeArea.showContextMenu()
                                }
                            }
                            KeyEvent.KEYCODE_BACK -> {
                                if (fileHandler.isModified) {
                                    releaseFile { finish() }
                                } else {
                                    if (System.currentTimeMillis() - lastBackKeyPressTime < DOUBLE_BACK_KEY_INTERVAL) {
                                        finish()
                                    } else {
                                        lastBackKeyPressTime = System.currentTimeMillis()
                                        Toast.makeText(this@MainActivity, resources.getText(R.string.confirm_exit), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
                                // Already handled in ACTION_DOWN
                            }
                            else -> {
                                // TODO Do this on key up?
                                codeArea.commandHandler.keyPressed(keyEvent)
                            }
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

    private fun reportException(exception: Throwable) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.error_exception)
        builder.setMessage(exception.localizedMessage)
        builder.setNegativeButton(R.string.button_close, null)
        val alertDialog = builder.create()
        alertDialog.show()
    }

    enum class FallbackFileType {
        FILE, TABLE_FILE
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