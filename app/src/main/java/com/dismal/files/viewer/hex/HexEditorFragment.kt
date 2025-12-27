/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package com.dismal.files.viewer.hex

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import com.dismal.files.R
import com.dismal.files.databinding.HexEditorFragmentBinding
import com.dismal.files.file.fileProviderUri
import com.dismal.files.util.ParcelableArgs
import com.dismal.files.util.args
import com.dismal.files.util.extraPath
import com.dismal.files.util.showToast
import com.dismal.files.util.fadeInUnsafe
import com.dismal.files.util.fadeOutUnsafe
import com.dismal.files.viewer.hex.MaterialHexView
import org.exbin.bined.basic.CodeAreaViewMode
import org.exbin.bined.editor.android.BinEdFileHandler
import org.exbin.auxiliary.binary_data.delta.SegmentsRepository
import org.exbin.auxiliary.binary_data.jna.JnaBufferEditableData
import org.exbin.bined.operation.BinaryDataUndoRedoChangeListener
import org.exbin.framework.bined.FileHandlingMode
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.FrameLayout
import android.text.InputType
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.content.res.Configuration
import org.exbin.bined.basic.BasicCodeAreaSection
import org.exbin.bined.android.basic.color.BasicCodeAreaColorsProfile
import org.exbin.bined.basic.MovementDirection
import org.exbin.bined.basic.SelectingMode
import org.exbin.bined.android.basic.DefaultCodeAreaCommandHandler

class HexEditorFragment : Fragment(), com.dismal.files.viewer.text.ConfirmCloseDialogFragment.Listener {
    private val args by args<Args>()
    private lateinit var argsFile: Path

    private lateinit var binding: HexEditorFragmentBinding
    private lateinit var codeArea: MaterialHexView
    private lateinit var fileHandler: BinEdFileHandler
    private lateinit var segmentsRepository: SegmentsRepository
    private lateinit var onBackPressedCallback: androidx.activity.OnBackPressedCallback

    private var lastSearchPattern: ByteArray? = null
    
    private var isModified = false
        set(value) {
            if (field != value) {
                field = value
                updateTitle()
                onBackPressedCallback.isEnabled = value
            }
        }
    
    private var isBindingData = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HexEditorFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val argsFile = args.intent.extraPath
        if (argsFile == null) {
            showToast(R.string.hex_viewer_error_no_file)
            finish()
            return
        }
        this.argsFile = argsFile

        val activity = requireActivity() as AppCompatActivity
        activity.lifecycleScope.launchWhenCreated {
            activity.setSupportActionBar(binding.toolbar)
            activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        }

        initializeHexEditor()
        
        onBackPressedCallback = object : androidx.activity.OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                showConfirmExitDialog()
            }
        }
        activity.onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback)
        
        loadFile()
    }


    private fun initializeHexEditor() {
        try {
            codeArea = binding.codeArea

            // MaterialHexView handles dual view by default.
            // We use a dummy for BinEdFileHandler to keep its data-loading logic
            // without needing the full CodeArea UI component.
            fileHandler = BinEdFileHandler(org.exbin.bined.android.basic.CodeArea(requireContext(), null as android.util.AttributeSet?))
            codeArea.undoRedo = fileHandler.undoRedo
            codeArea.internalCodeArea = fileHandler.codeArea
            
            segmentsRepository = SegmentsRepository { JnaBufferEditableData() }
            fileHandler.segmentsRepository = segmentsRepository
            fileHandler.setNewData(FileHandlingMode.DELTA)
            
            // Set up listeners
            fileHandler.undoRedo.addChangeListener(BinaryDataUndoRedoChangeListener {
                updateUndoRedoState()
                if (!isBindingData) {
                    isModified = true
                }
                codeArea.invalidate()
            })
            
            // Set up cursor position listener
            codeArea.setOnCaretMovedListener { position ->
                updateStatusPanel(position)
                updateKeypadVisibility()
            }
            
            codeArea.isFocusable = true
            codeArea.isFocusableInTouchMode = true
            
            updateTitle()
            updateStatusPanel()
            setupKeypad()
            
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Error initializing hex editor: ${e.message}")
        }
    }

    private fun setupKeypad() {
        val keypad = binding.root.findViewById<ViewGroup>(R.id.keyPanel) ?: return
        
        // Hex buttons
        val hexChars = "0123456789ABCDEF"
        for (c in hexChars) {
            val buttonId = resources.getIdentifier("button$c", "id", requireContext().packageName)
            binding.root.findViewById<Button>(buttonId)?.apply {
                 setOnClickListener { typeChar(c) }
                 isFocusable = false 
            }
        }

        // Navigation
        val navButtons = listOf(
            R.id.buttonLeft to KeyEvent.KEYCODE_DPAD_LEFT,
            R.id.buttonRight to KeyEvent.KEYCODE_DPAD_RIGHT,
            R.id.buttonUp to KeyEvent.KEYCODE_DPAD_UP,
            R.id.buttonDown to KeyEvent.KEYCODE_DPAD_DOWN,
            R.id.buttonHome to KeyEvent.KEYCODE_MOVE_HOME,
            R.id.buttonEnd to KeyEvent.KEYCODE_MOVE_END,
            R.id.buttonDelete to KeyEvent.KEYCODE_FORWARD_DEL,
            R.id.buttonInsert to KeyEvent.KEYCODE_INSERT,
            R.id.buttonTab to KeyEvent.KEYCODE_TAB
        )
        
        for ((id, code) in navButtons) {
            binding.root.findViewById<Button>(id)?.apply {
                setOnClickListener { sendKey(code) }
                isFocusable = false
            }
        }
        
        binding.root.findViewById<Button>(R.id.buttonBk)?.apply {
            setOnClickListener { sendKey(KeyEvent.KEYCODE_DEL) }
            isFocusable = false
        }
        
        updateKeypadVisibility()
    }
    
    // Helper to send KeyEvent/Command
    private fun sendKey(keyCode: Int) {
        codeArea.requestFocus()
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> codeArea.move(MovementDirection.LEFT)
            KeyEvent.KEYCODE_DPAD_RIGHT -> codeArea.move(MovementDirection.RIGHT)
            KeyEvent.KEYCODE_DPAD_UP -> codeArea.move(MovementDirection.UP)
            KeyEvent.KEYCODE_DPAD_DOWN -> codeArea.move(MovementDirection.DOWN)
            KeyEvent.KEYCODE_MOVE_HOME -> codeArea.move(MovementDirection.ROW_START)
            KeyEvent.KEYCODE_MOVE_END -> codeArea.move(MovementDirection.ROW_END)
            KeyEvent.KEYCODE_FORWARD_DEL -> codeArea.deleteData()
            KeyEvent.KEYCODE_INSERT -> {
                codeArea.toggleEditOperation()
                updateStatusPanel() // To show operation if needed
            }
            KeyEvent.KEYCODE_DEL -> {
                codeArea.backspace()
            }
            else -> {
                // For other keys, we still might want to dispatch or handle
            }
        }
        codeArea.invalidate()
    }

    // Helper to type char
    private fun typeChar(c: Char) {
        codeArea.requestFocus()
        codeArea.typeChar(c)
        codeArea.invalidate()
    }
    
    private fun updateKeypadVisibility() {
        val keypad = binding.root.findViewById<View>(R.id.keyPanel) ?: return
        val showKeypad = codeArea.activeSection == BasicCodeAreaSection.CODE_MATRIX
        
        if (showKeypad) {
            if (keypad.visibility != View.VISIBLE) {
                keypad.visibility = View.VISIBLE
            }
            // Hide Soft Keyboard
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(codeArea.windowToken, 0)
        } else {
            if (keypad.visibility != View.GONE) {
                keypad.visibility = View.GONE
            }
            // Show Soft Keyboard
            codeArea.requestFocus()
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(codeArea, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.hex_editor, menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.action_find_next)?.isEnabled = lastSearchPattern != null
        super.onPrepareOptionsMenu(menu)
        updateUndoRedoState()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_save -> {
                saveFile()
                true
            }
            R.id.action_undo -> {
                if (fileHandler.undoRedo.canUndo()) {
                    fileHandler.undoRedo.performUndo()
                }
                true
            }
            R.id.action_redo -> {
                if (fileHandler.undoRedo.canRedo()) {
                    fileHandler.undoRedo.performRedo()
                }
                true
            }
            R.id.action_find -> {
                showFindDialog()
                true
            }
            R.id.action_find_next -> {
                lastSearchPattern?.let {
                    if (!codeArea.findNext(it)) {
                        showToast("Pattern not found")
                    }
                }
                true
            }
            R.id.action_goto -> {
                showGoToDialog()
                true
            }
            R.id.action_reload -> {
                loadFile()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    fun onSupportNavigateUp(): Boolean {
        if (isModified) {
            showConfirmExitDialog()
            return true
        }
        return false
    }

    override fun finish() {
        requireActivity().finish()
    }

    private fun loadFile() {
        binding.progress.fadeInUnsafe()
        binding.errorText.fadeOutUnsafe()
        binding.codeArea.fadeOutUnsafe()
        
        lifecycleScope.launch {
            try {
                val uri = argsFile.fileProviderUri
                withContext(Dispatchers.IO) {
                    isBindingData = true
                    fileHandler.openFile(requireContext().contentResolver, uri, fileHandler.fileHandlingMode)
                }

                // Sync data to our native view
                val data = fileHandler.codeArea.contentData
                codeArea.binaryData = data

                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.codeArea.fadeInUnsafe()
                
                
                isBindingData = false
                isModified = false
                
                updateTitle()
                
            } catch (e: Exception) {
                e.printStackTrace()
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeInUnsafe()
                binding.errorText.text = "Error loading file: ${e.message}"
            }
        }
    }

    private fun saveFile() {
        lifecycleScope.launch {
            try {
                val uri = argsFile.fileProviderUri
                withContext(Dispatchers.IO) {
                    fileHandler.saveFile(requireContext().contentResolver, uri)
                }

                showToast(R.string.hex_editor_save_success)
                isModified = false

            } catch (e: Exception) {
                e.printStackTrace()
                showToast(getString(R.string.hex_editor_save_error) + ": ${e.message}")
            }
        }
    }

    private fun updateUndoRedoState() {
        val menu = binding.toolbar.menu
        menu.findItem(R.id.action_undo)?.isEnabled = fileHandler.undoRedo.canUndo()
        menu.findItem(R.id.action_redo)?.isEnabled = fileHandler.undoRedo.canRedo()
    }

    private fun updateStatusPanel(caretPosition: Long = codeArea.caretPosition) {
        binding.cursorPositionValue.text = caretPosition.toString()
        binding.documentSizeValue.text = codeArea.binaryData?.dataSize?.toString() ?: "0"

        // Show edit mode (Insert vs Overwrite)
        val modeText = if (codeArea.editOperation == org.exbin.bined.EditOperation.INSERT) "INS" else "OVR"
        binding.cursorPositionLabel.text = "Position ($modeText): "
    }

    private fun updateTitle() {
        val fileName = argsFile.fileName.toString()
        requireActivity().title = if (isModified) {
            getString(R.string.text_editor_title_changed_format, fileName)
        } else {
            getString(R.string.hex_viewer_title_format, fileName)
        }
    }

    private fun showGoToDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "Position (e.g. 1024 or 0x400)"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val container = FrameLayout(requireContext()).apply {
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(editText)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.hex_editor_goto)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val text = editText.text.toString().trim()
                try {
                    val position = if (text.startsWith("0x", true)) {
                        text.substring(2).toLong(16)
                    } else {
                        text.toLong()
                    }
                    codeArea.jumpToPosition(position)
                } catch (e: Exception) {
                    showToast("Invalid position")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFindDialog() {
        val editText = EditText(requireContext()).apply {
            hint = "Search pattern (ASCII or Hex 0x...)"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val container = FrameLayout(requireContext()).apply {
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            addView(editText)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.search)
            .setView(container)
            .setPositiveButton(R.string.search) { _, _ ->
                val text = editText.text.toString().trim()
                if (text.isEmpty()) return@setPositiveButton
                
                val pattern = if (text.startsWith("0x", true)) {
                    val hexStr = text.substring(2).replace(" ", "")
                    try {
                        hexStr.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    } catch (e: Exception) {
                        showToast("Invalid hex pattern")
                        return@setPositiveButton
                    }
                } else {
                    text.toByteArray()
                }

                lastSearchPattern = pattern
                if (!codeArea.findNext(pattern)) {
                    showToast("Pattern not found")
                }
                activity?.invalidateOptionsMenu()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs
    private fun showConfirmExitDialog() {
        com.dismal.files.viewer.text.ConfirmCloseDialogFragment.show(this)
    }
    
    // We implement the listener from ConfirmCloseDialogFragment if we want to reuse it nicely,
    // assuming it calls a method on us.
    // The ConfirmCloseDialogFragment in text package calls (parentFragment as Listener).finish()
    // So we need to implement that interface.
}