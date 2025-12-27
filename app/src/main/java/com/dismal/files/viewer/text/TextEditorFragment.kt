/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package com.dismal.files.viewer.text

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import java8.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import com.dismal.files.R
import com.dismal.files.databinding.TextEditorFragmentBinding
import com.dismal.files.ui.ThemedFastScroller
import com.dismal.files.util.ActionState
import com.dismal.files.util.DataState
import org.eclipse.tm4e.core.registry.IThemeSource
import com.dismal.files.util.ParcelableArgs
import com.dismal.files.util.addOnBackPressedCallback
import com.dismal.files.util.args
import com.dismal.files.util.extraPath
import com.dismal.files.util.fadeInUnsafe
import com.dismal.files.util.fadeOutUnsafe
import com.dismal.files.util.isReady
import com.dismal.files.util.showToast
import com.dismal.files.util.viewModels
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.io.InputStreamReader
import androidx.appcompat.app.AppCompatDelegate
import java.nio.charset.Charset
import io.github.rosemoe.sora.widget.EditorSearcher
import android.text.Editable
import android.text.TextWatcher
import androidx.core.view.isVisible
import com.dismal.files.databinding.TextEditorFindReplaceBarBinding

class TextEditorFragment : Fragment(), ConfirmReloadDialogFragment.Listener,
    ConfirmCloseDialogFragment.Listener {
    private val args by args<Args>()
    private lateinit var argsFile: Path

    private lateinit var binding: TextEditorFragmentBinding

    private lateinit var menuBinding: MenuBinding

    private val viewModel by viewModels { { TextEditorViewModel(argsFile) } }

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private var isSettingText = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        lifecycleScope.launchWhenStarted {
            onBackPressedCallback = object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    ConfirmCloseDialogFragment.show(this@TextEditorFragment)
                }
            }
            launch {
                viewModel.isTextChanged.collect {
                    onBackPressedCallback.isEnabled = viewModel.isTextChanged.value
                }
            }
            addOnBackPressedCallback(onBackPressedCallback)

            launch { viewModel.encoding.collect { onEncodingChanged(it) } }
            launch { viewModel.textState.collect { onTextStateChanged(it) } }
            launch { viewModel.isTextChanged.collect { onIsTextChangedChanged(it) } }
            launch { viewModel.writeFileState.collect { onWriteFileStateChanged(it) } }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        TextEditorFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val argsFile = args.intent.extraPath
        if (argsFile == null) {
            // TODO: Show a toast.
            finish()
            return
        }
        this.argsFile = argsFile

        val activity = requireActivity() as AppCompatActivity
        activity.lifecycleScope.launchWhenCreated {
            activity.setSupportActionBar(binding.toolbar)
            activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        }

        // TODO: Move reload-prevent here so that we can also handle save-as, etc. Or maybe just get
        //  rid of the mPathLiveData in TextEditorViewModel.
        // Manually save and restore state in view model to avoid TransactionTooLargeException.
        binding.textEdit.isSaveEnabled = false
        // SoraEditor handles its own scroll, so we don't need ThemedFastScroller on it directly if it's match_parent
        
        initializeSoraEditor()

        binding.textEdit.subscribeEvent(io.github.rosemoe.sora.event.ContentChangeEvent::class.java) { _ , _ ->
            if (isSettingText) {
                return@subscribeEvent
            }
            // Might happen if the animation is running and user is quick enough.
            if (viewModel.textState.value !is DataState.Success) {
                return@subscribeEvent
            }
            viewModel.isTextChanged.value = true
        }

        initializeFindReplaceBar()
        
        // Initialize error detection
        initializeErrorDetection()

        // TODO: Request storage permission if not granted.
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        // SoraEditor state saving might need careful handling if large. 
        // For now, let's keep it simple.
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        menuBinding = MenuBinding.inflate(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        updateSaveMenuItem()
        updateEncodingMenuItems()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_save -> {
                save()
                true
            }
            R.id.action_undo -> {
                binding.textEdit.undo()
                true
            }
            R.id.action_redo -> {
                binding.textEdit.redo()
                true
            }
            R.id.action_find_replace -> {
                showFindReplaceBar()
                true
            }
            R.id.action_reload -> {
                onReload()
                true
            }
            Menu.FIRST -> {
                viewModel.encoding.value = Charset.forName(item.titleCondensed!!.toString())
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    fun onSupportNavigateUp(): Boolean {
        if (onBackPressedCallback.isEnabled) {
            onBackPressedCallback.handleOnBackPressed()
            return true
        }
        return false
    }

    override fun finish() {
        requireActivity().finish()
    }

    private fun onEncodingChanged(encoding: Charset) {
        updateEncodingMenuItems()
    }

    private fun updateEncodingMenuItems() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        val charsetName = viewModel.encoding.value.name()
        val charsetItem = menuBinding.encodingSubMenu.children
            .find { it.titleCondensed == charsetName }!!
        charsetItem.isChecked = true
    }

    private fun onTextStateChanged(state: DataState<String>) {
        updateTitle()
        when (state) {
            is DataState.Loading -> {
                binding.progress.fadeInUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.textEdit.fadeOutUnsafe()
            }
            is DataState.Success -> {
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.textEdit.fadeInUnsafe()
                if (!viewModel.isTextChanged.value) {
                    setText(state.data)
                }
            }
            is DataState.Error -> {
                state.throwable.printStackTrace()
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeInUnsafe()
                binding.errorText.text = state.throwable.toString()
                binding.textEdit.fadeOutUnsafe()
            }
        }
    }

    private fun setText(text: String?) {
        isSettingText = true
        binding.textEdit.setText(text)
        isSettingText = false
        viewModel.isTextChanged.value = false
        
        // Apply language after text is set and editor is initialized
        // Add a longer delay to ensure Sora Editor and grammars are fully initialized
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500) // Longer delay to ensure initialization
            applyLanguage()
        }
    }

    private fun initializeSoraEditor() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val context = requireContext().applicationContext
                
                // Check if we're in a debug or release build
                val isDebugBuild = try {
                    (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                } catch (e: Exception) {
                    false
                }
                Log.d("TextEditor", "Build type: ${if (isDebugBuild) "DEBUG" else "RELEASE"}")
                
                // Test direct asset access first
                try {
                    val testStream = context.assets.open("textmate/languages.json")
                    val testContent = testStream.bufferedReader().use { it.readText() }
                    testStream.close()
                    Log.d("TextEditor", "Direct asset access successful, languages.json size: ${testContent.length}")
                } catch (e: Exception) {
                    Log.e("TextEditor", "Direct asset access failed - TextMate assets not available", e)
                    withContext(Dispatchers.Main) {
                        // Fallback to basic editor setup
                        binding.textEdit.typefaceText = Typeface.MONOSPACE
                    }
                    return@launch
                }
                
                // Initialize file provider registry with obfuscation-safe approach
                val fileProviderRegistry = try {
                    FileProviderRegistry.getInstance()
                } catch (e: Exception) {
                    Log.e("TextEditor", "Failed to get FileProviderRegistry instance - likely obfuscation issue", e)
                    return@launch
                }
                
                // Add our assets provider with error handling
                val assetsProvider = try {
                    AssetsFileResolver(context.assets)
                } catch (e: Exception) {
                    Log.e("TextEditor", "Failed to create AssetsFileResolver - likely obfuscation issue", e)
                    return@launch
                }
                
                try {
                    fileProviderRegistry.addFileProvider(assetsProvider)
                    Log.d("TextEditor", "Added AssetsFileResolver to FileProviderRegistry")
                } catch (e: Exception) {
                    Log.e("TextEditor", "Failed to add AssetsFileResolver - likely obfuscation issue", e)
                    return@launch
                }
                
                // Test FileProviderRegistry access with detailed error reporting
                try {
                    val registryStream = fileProviderRegistry.tryGetInputStream("textmate/languages.json")
                    if (registryStream != null) {
                        val registryContent = registryStream.bufferedReader().use { it.readText() }
                        registryStream.close()
                        Log.d("TextEditor", "FileProviderRegistry access successful, size: ${registryContent.length}")
                    } else {
                        Log.e("TextEditor", "FileProviderRegistry returned null - likely obfuscation broke method calls")
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("TextEditor", "FileProviderRegistry access failed - likely obfuscation issue", e)
                    return@launch
                }

                // Load grammars with obfuscation-aware error handling
                try {
                    val grammarRegistry = GrammarRegistry.getInstance()
                    grammarRegistry.loadGrammars("textmate/languages.json")
                    Log.d("TextEditor", "Successfully loaded TextMate grammars")
                    
                    // Verify some grammars are loaded
                    val testScopes = listOf("source.java", "source.python", "source.shell", "text.plain")
                    var loadedCount = 0
                    for (scope in testScopes) {
                        try {
                            val grammar = grammarRegistry.findGrammar(scope)
                            val status = if (grammar != null) {
                                loadedCount++
                                "LOADED"
                            } else "MISSING"
                            Log.d("TextEditor", "Grammar check - $scope: $status")
                        } catch (e: Exception) {
                            Log.e("TextEditor", "Error checking grammar $scope - likely obfuscation issue", e)
                        }
                    }
                    
                    if (loadedCount == 0) {
                        Log.e("TextEditor", "No grammars were loaded successfully - likely obfuscation broke grammar loading")
                        return@launch
                    } else {
                        Log.d("TextEditor", "Successfully loaded $loadedCount/${testScopes.size} test grammars")
                    }
                } catch (e: Exception) {
                    Log.e("TextEditor", "Failed to load grammars - likely obfuscation issue", e)
                    return@launch
                }

                // Load theme with obfuscation-safe approach
                val isDark = isDarkMode()
                val themeName = if (isDark) "darcula" else "quietlight"
                
                try {
                    val themeRegistry = ThemeRegistry.getInstance()
                    
                    if (!themeRegistry.setTheme(themeName)) {
                        val themePath = "textmate/$themeName.json"
                        val themeInputStream = fileProviderRegistry.tryGetInputStream(themePath)
                        if (themeInputStream != null) {
                            val themeModel = ThemeModel(
                                IThemeSource.fromInputStream(
                                    themeInputStream,
                                    "$themeName.json",
                                    null
                                ),
                                themeName
                            )
                            themeModel.isDark = isDark
                            themeRegistry.loadTheme(themeModel)
                            themeRegistry.setTheme(themeName)
                            Log.d("TextEditor", "Successfully loaded theme: $themeName")
                        } else {
                            Log.w("TextEditor", "Could not load theme: $themeName - FileProviderRegistry returned null")
                        }
                    } else {
                        Log.d("TextEditor", "Theme already set: $themeName")
                    }
                } catch (e: Exception) {
                    Log.e("TextEditor", "Failed to load theme: $themeName - likely obfuscation issue", e)
                    // Continue with default theme
                }

                withContext(Dispatchers.Main) {
                    try {
                        // Apply color scheme with obfuscation-safe approach
                        val themeRegistry = ThemeRegistry.getInstance()
                        val colorScheme = TextMateColorScheme.create(themeRegistry)
                        binding.textEdit.colorScheme = colorScheme
                        
                        // Debug color scheme
                        try {
                            Log.d("TextEditor", "Color scheme applied: ${colorScheme.javaClass.simpleName}")
                            
                            // Check if it's actually a TextMate color scheme
                            if (colorScheme is TextMateColorScheme) {
                                Log.d("TextEditor", "✅ TextMate color scheme confirmed")
                            } else {
                                Log.w("TextEditor", "❌ Not a TextMate color scheme: ${colorScheme.javaClass}")
                            }
                            
                            // Log some key colors
                            val textColor = colorScheme.getColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.TEXT_NORMAL)
                            val bgColor = colorScheme.getColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.WHOLE_BACKGROUND)
                            val keywordColor = colorScheme.getColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.KEYWORD)
                            val stringColor = colorScheme.getColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LITERAL)
                            
                            Log.d("TextEditor", "Colors - Text: #${Integer.toHexString(textColor)}, BG: #${Integer.toHexString(bgColor)}")
                            Log.d("TextEditor", "Colors - Keyword: #${Integer.toHexString(keywordColor)}, String: #${Integer.toHexString(stringColor)}")
                            
                        } catch (e: Exception) {
                            Log.w("TextEditor", "Could not debug color scheme", e)
                        }
                        
                        // Configure editor properties
                        binding.textEdit.typefaceText = Typeface.MONOSPACE
                        binding.textEdit.props.apply {
                            this.deleteMultiSpaces = 4
                            this.useICULibToSelectWords = true
                        }
                        
                        // Configure editor features
                        binding.textEdit.apply {
                            tabWidth = 4
                            isLineNumberEnabled = true
                            isWordwrap = false
                            
                            // Enable auto-completion features (using available properties)
                            props.apply {
                                // Enable auto-completion popup
                                autoCompletionOnComposing = true
                                
                                // Try to set completion properties if they exist
                                try {
                                    val propsClass = this.javaClass
                                    
                                    // Try newer property names that might exist in 0.23.4
                                    val propertiesToTry = listOf(
                                        "maxCompletionItemCount" to 50,
                                        "completionWndDelay" to 50,
                                        "completionWndMinPrefix" to 1,
                                        "autoCompletionDelay" to 50,
                                        "completionItemCount" to 50,
                                        "completionMinPrefix" to 1
                                    )
                                    
                                    for ((propertyName, value) in propertiesToTry) {
                                        try {
                                            val field = propsClass.getDeclaredField(propertyName)
                                            field.isAccessible = true
                                            field.setInt(this, value)
                                            Log.d("TextEditor", "Set $propertyName to $value")
                                        } catch (e: Exception) {
                                            // Property doesn't exist, continue
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.d("TextEditor", "Could not set completion properties")
                                }
                            }
                            
                            // Log current auto-completion settings
                            Log.d("TextEditor", "Auto-completion on composing: ${props.autoCompletionOnComposing}")
                            
                            // Add key listener for manual completion trigger (Ctrl+Space)
                            setOnKeyListener { _, keyCode, event ->
                                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                                    if (keyCode == android.view.KeyEvent.KEYCODE_SPACE && event.isCtrlPressed) {
                                        Log.d("TextEditor", "Ctrl+Space pressed - trying to trigger completion")
                                        try {
                                            // Try different methods to trigger completion
                                            val methods = listOf("requireCompletion", "showAutoCompleteWindow", "showCompletionWindow")
                                            val editorClass = binding.textEdit.javaClass
                                            for (methodName in methods) {
                                                try {
                                                    val method = editorClass.getMethod(methodName)
                                                    method.invoke(binding.textEdit)
                                                    Log.d("TextEditor", "Successfully called $methodName")
                                                    return@setOnKeyListener true
                                                } catch (e: Exception) {
                                                    // Try next method
                                                }
                                            }
                                            Log.d("TextEditor", "No completion methods found")
                                        } catch (e: Exception) {
                                            Log.w("TextEditor", "Failed to trigger completion", e)
                                        }
                                        return@setOnKeyListener true
                                    }
                                }
                                false
                            }
                        }
                        
                        // Force refresh
                        binding.textEdit.invalidate()
                        
                        Log.d("TextEditor", "Sora Editor initialized successfully with TextMate support")
                    } catch (e: Exception) {
                        Log.e("TextEditor", "Failed to configure editor UI - likely obfuscation issue", e)
                        // Fallback to basic configuration
                        binding.textEdit.typefaceText = Typeface.MONOSPACE
                    }
                }
            } catch (e: Exception) {
                Log.e("TextEditor", "Critical error in Sora Editor initialization - likely obfuscation issue", e)
                withContext(Dispatchers.Main) {
                    // Fallback to basic editor setup
                    binding.textEdit.typefaceText = Typeface.MONOSPACE
                }
            }
        }
    }

    private fun isDarkMode(): Boolean {
        val nightMode = AppCompatDelegate.getDefaultNightMode()
        if (nightMode == AppCompatDelegate.MODE_NIGHT_YES) return true
        if (nightMode == AppCompatDelegate.MODE_NIGHT_NO) return false
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun applyLanguage() {
        val extension = argsFile.fileName.toString().substringAfterLast('.', "")
        val scopeName = getLanguageScopeName(extension)
        
        Log.d("TextEditor", "Applying language for extension: $extension, scopeName: $scopeName")
        
        if (scopeName == null) {
            Log.d("TextEditor", "No language found for extension: $extension, using plain text")
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // First, verify that grammars are loaded
                val grammarRegistry = GrammarRegistry.getInstance()
                val grammar = grammarRegistry.findGrammar(scopeName)
                
                if (grammar == null) {
                    Log.w("TextEditor", "Grammar not found for scope: $scopeName, trying to reload grammars")
                    
                    // Try to reload grammars if they're not found
                    try {
                        grammarRegistry.loadGrammars("textmate/languages.json")
                        Log.d("TextEditor", "Reloaded grammars, trying again")
                        
                        // Try again after reload
                        val retryGrammar = grammarRegistry.findGrammar(scopeName)
                        if (retryGrammar == null) {
                            Log.w("TextEditor", "Grammar still not found after reload: $scopeName")
                            withContext(Dispatchers.Main) {
                                // Just use plain text editor without TextMate
                                Log.d("TextEditor", "Using plain text editor for unsupported language")
                            }
                            return@launch
                        }
                    } catch (e: Exception) {
                        Log.e("TextEditor", "Failed to reload grammars", e)
                        return@launch
                    }
                }
                
                // Create TextMate language using scope name
                val language = TextMateLanguage.create(scopeName, true)
                Log.d("TextEditor", "Created TextMate language for $scopeName")
                
                // Verify the language has syntax highlighting capabilities
                try {
                    // Try to access analyzer if available
                    val languageClass = language.javaClass
                    val analyzerMethod = languageClass.getMethod("getAnalyzer")
                    val analyzer = analyzerMethod.invoke(language)
                    Log.d("TextEditor", "Language analyzer: ${analyzer?.javaClass?.simpleName}")
                } catch (e: Exception) {
                    Log.d("TextEditor", "Language analyzer not accessible in this version")
                }
                
                // Try to load keywords if available
                try {
                    requireContext().assets.open("textmate/keywords.json").use { inputStream ->
                        val jsonString = inputStream.bufferedReader().use { reader -> reader.readText() }
                        val jsonObject = org.json.JSONObject(jsonString)
                        val keywordsArray = jsonObject.optJSONArray(scopeName)
                        if (keywordsArray != null) {
                            val keywords = Array(keywordsArray.length()) { i ->
                                keywordsArray.getString(i)
                            }
                            language.setCompleterKeywords(keywords)
                            Log.d("TextEditor", "Loaded ${keywords.size} keywords for $scopeName")
                            
                            // Debug: Print first few keywords
                            val firstKeywords = keywords.take(5).joinToString(", ")
                            Log.d("TextEditor", "Sample keywords: $firstKeywords")
                            
                            // Try to verify keywords were set and test different completion approaches
                            val languageClass = language.javaClass
                            try {
                                // Method 1: Try to access auto-complete provider using reflection
                                val providerMethod = languageClass.getMethod("getAutoCompleteProvider")
                                val completer = providerMethod.invoke(language)
                                Log.d("TextEditor", "Auto-complete provider: ${completer?.javaClass?.simpleName}")
                            } catch (e: Exception) {
                                Log.d("TextEditor", "getAutoCompleteProvider not available, trying alternatives")
                                
                                // Method 2: Try to access completer directly
                                try {
                                    val completerField = languageClass.getDeclaredField("completer")
                                    completerField.isAccessible = true
                                    val completer = completerField.get(language)
                                    Log.d("TextEditor", "Direct completer access: ${completer?.javaClass?.simpleName}")
                                } catch (e2: Exception) {
                                    Log.d("TextEditor", "Direct completer access failed")
                                }
                                
                                // Method 3: Check if language has completion methods
                                try {
                                    val methods = languageClass.methods
                                    val completionMethods = methods.filter { it.name.contains("complet", ignoreCase = true) }
                                    Log.d("TextEditor", "Available completion methods: ${completionMethods.map { it.name }}")
                                } catch (e3: Exception) {
                                    Log.d("TextEditor", "Could not list completion methods")
                                }
                            }
                        } else {
                            Log.d("TextEditor", "No keywords found for scope: $scopeName")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("TextEditor", "Could not load keywords for $scopeName", e)
                }
                
                withContext(Dispatchers.Main) {
                    binding.textEdit.setEditorLanguage(language)
                    
                    // Reapply color scheme after language is set
                    try {
                        val themeRegistry = ThemeRegistry.getInstance()
                        binding.textEdit.colorScheme = TextMateColorScheme.create(themeRegistry)
                        Log.d("TextEditor", "Reapplied TextMate color scheme after language setting")
                    } catch (e: Exception) {
                        Log.w("TextEditor", "Failed to reapply color scheme", e)
                    }
                    
                    // Test auto-completion manually
                    try {
                        // Add a test to see if completion works
                        binding.textEdit.postDelayed({
                            try {
                                // Try to trigger completion if method exists - try more method names for 0.23.4
                                val editorClass = binding.textEdit.javaClass
                                val methodsToTry = listOf(
                                    "requireCompletion",
                                    "showAutoCompleteWindow", 
                                    "showCompletionWindow",
                                    "requestCompletion",
                                    "triggerCompletion",
                                    "showCompletion",
                                    "requireAutoCompletion"
                                )
                                
                                for (methodName in methodsToTry) {
                                    try {
                                        val completionMethod = editorClass.getMethod(methodName)
                                        completionMethod.invoke(binding.textEdit)
                                        Log.d("TextEditor", "Auto-completion test: $methodName triggered successfully")
                                        return@postDelayed
                                    } catch (e: Exception) {
                                        // Try next method
                                    }
                                }
                                
                                Log.d("TextEditor", "No auto-completion trigger methods found")
                            } catch (e: Exception) {
                                Log.d("TextEditor", "Auto-completion test failed: ${e.message}")
                            }
                        }, 1000)
                    } catch (e: Exception) {
                        Log.w("TextEditor", "Could not test auto-completion", e)
                    }
                    
                    // Force refresh to apply syntax highlighting
                    binding.textEdit.invalidate()
                    binding.textEdit.requestLayout()
                    
                    Log.d("TextEditor", "Successfully applied language: $scopeName for extension: $extension")
                }
            } catch (e: Exception) {
                Log.e("TextEditor", "Failed to apply language $scopeName for extension $extension", e)
                withContext(Dispatchers.Main) {
                    // Don't try to create fallback language if grammars aren't loaded
                    Log.d("TextEditor", "Using basic editor without TextMate language support")
                }
            }
        }
    }



    private fun getLanguageScopeName(extension: String): String? {
        return when (extension.lowercase()) {
            "js", "mjs", "cjs" -> "source.js"
            "ts", "mts", "cts" -> "source.ts"
            "jsx" -> "source.js.jsx"
            "tsx" -> "source.tsx"
            "html", "htm", "xhtml" -> "text.html.basic"
            "css" -> "source.css"
            "scss", "sass" -> "source.css.scss"
            "less" -> "source.css.less"
            "json" -> "source.json"
            "md", "markdown" -> "text.html.markdown"
            "xml", "svg" -> "text.xml"
            "yaml", "yml" -> "source.yaml"
            "py", "pyi", "pyw" -> "source.python"
            "java" -> "source.java"
            "kt", "kts" -> "source.kotlin"
            "c" -> "source.c"
            "cpp", "cxx", "cc", "h", "hpp", "hxx" -> "source.cpp"
            "cs" -> "source.cs"
            "go" -> "source.go"
            "rs" -> "source.rust"
            "swift" -> "source.swift"
            "php", "phtml" -> "source.php"
            "rb", "ruby" -> "source.ruby"
            "sh", "bash", "zsh", "fish" -> "source.shell"
            "bat", "cmd" -> "source.batchfile"
            "toml" -> "source.toml"
            "ini", "cfg", "conf" -> "source.ini"
            "properties" -> "source.properties"
            "asm", "s" -> "source.asm"
            "sql" -> "source.sql"
            "lua" -> "source.lua"
            "dart" -> "source.dart"
            "zig" -> "source.zig"
            "nim" -> "source.nim"
            "pas", "pascal" -> "source.pascal"
            "coq" -> "source.coq"
            "smali" -> "source.smali"
            "ps1", "psm1" -> "source.powershell"
            "log" -> "text.log"
            "tex", "latex" -> "text.tex.latex"
            "lisp", "lsp" -> "source.lisp"
            "groovy", "gradle" -> "source.groovy"
            "gitignore", "dockerignore" -> "source.ignore"
            "txt", "text" -> "text.plain"
            else -> null
        }
    }

    private fun onIsTextChangedChanged(changed: Boolean) {
        updateTitle()
    }

    private fun updateTitle() {
        val fileName = viewModel.file.value.fileName.toString()
        val changed = viewModel.isTextChanged.value
        requireActivity().title = getString(
            if (changed) {
                R.string.text_editor_title_changed_format
            } else {
                R.string.text_editor_title_format
            }, fileName
        )
    }

    private fun onReload() {
        if (viewModel.isTextChanged.value) {
            ConfirmReloadDialogFragment.show(this)
        } else {
            reload()
        }
    }

    override fun reload() {
        viewModel.isTextChanged.value = false
        viewModel.reload()
    }

    private fun save() {
        val text = binding.textEdit.text.toString()
        viewModel.writeFile(argsFile, text, requireContext())
    }

    private fun onWriteFileStateChanged(state: ActionState<Pair<Path, String>, Unit>) {
        when (state) {
            is ActionState.Ready, is ActionState.Running -> updateSaveMenuItem()
            is ActionState.Success -> {
                showToast(R.string.text_editor_save_success)
                viewModel.finishWritingFile()
                viewModel.isTextChanged.value = false
            }
            // The error will be toasted by service so we should never show it in UI.
            is ActionState.Error -> viewModel.finishWritingFile()
        }
    }

    private fun updateSaveMenuItem() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        menuBinding.saveItem.isEnabled = viewModel.writeFileState.value.isReady
    }

    private fun initializeFindReplaceBar() {
        val barBinding = TextEditorFindReplaceBarBinding.bind(binding.findReplaceBar.root)
        barBinding.findEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateSearch()
            }
        })
        barBinding.regexCheckBox.setOnCheckedChangeListener { _, _ -> updateSearch() }
        barBinding.replaceCheckBox.setOnCheckedChangeListener { _, isChecked ->
            barBinding.replaceRow.isVisible = isChecked
        }
        barBinding.findNextButton.setOnClickListener { binding.textEdit.searcher.gotoNext() }
        barBinding.findPreviousButton.setOnClickListener { binding.textEdit.searcher.gotoPrevious() }
        barBinding.closeFindButton.setOnClickListener { hideFindReplaceBar() }
        barBinding.replaceButton.setOnClickListener {
            val replacement = barBinding.replaceEditText.text.toString()
            binding.textEdit.searcher.replaceThis(replacement)
        }
        barBinding.replaceAllButton.setOnClickListener {
            val replacement = barBinding.replaceEditText.text.toString()
            binding.textEdit.searcher.replaceAll(replacement)
        }
    }

    private fun showFindReplaceBar() {
        binding.findReplaceBar.root.isVisible = true
        TextEditorFindReplaceBarBinding.bind(binding.findReplaceBar.root).findEditText.requestFocus()
    }

    private fun hideFindReplaceBar() {
        binding.findReplaceBar.root.isVisible = false
        binding.textEdit.searcher.stopSearch()
        binding.textEdit.requestFocus()
    }

    private fun updateSearch() {
        val barBinding = TextEditorFindReplaceBarBinding.bind(binding.findReplaceBar.root)
        val query = barBinding.findEditText.text.toString()
        if (query.isEmpty()) {
            binding.textEdit.searcher.stopSearch()
            barBinding.matchCountText.text = ""
            return
        }
        val useRegex = barBinding.regexCheckBox.isChecked
        val options = EditorSearcher.SearchOptions(false, useRegex)
        try {
            binding.textEdit.searcher.search(query, options)
        } catch (e: java.util.regex.PatternSyntaxException) {
            binding.textEdit.searcher.stopSearch()
            barBinding.matchCountText.text = "Bad regex"
        }
        
        // Update match count (this might need to be done via a listener in SoraEditor if available)
        // For now, we'll leave it or check if searcher has a result count.
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.textEdit.release()
    }

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs

    private class MenuBinding private constructor(
        val menu: Menu,
        val saveItem: MenuItem,
        val encodingSubMenu: SubMenu
    ) {
        companion object {
            fun inflate(menu: Menu, inflater: MenuInflater): MenuBinding {
                inflater.inflate(R.menu.text_editor, menu)
                val encodingSubMenu = menu.findItem(R.id.action_encoding).subMenu!!
                for ((charsetName, charset) in Charset.availableCharsets()) {
                    // HACK: Use titleCondensed to store charset name.
                    encodingSubMenu.add(Menu.NONE, Menu.FIRST, Menu.NONE, charset.displayName())
                        .titleCondensed = charsetName
                }
                encodingSubMenu.setGroupCheckable(Menu.NONE, true, true)
                return MenuBinding(menu, menu.findItem(R.id.action_save), encodingSubMenu)
            }
        }
    }
    
    private fun initializeErrorDetection() {
        // Basic error detection for common syntax issues
        lifecycleScope.launch {
            binding.textEdit.subscribeEvent(io.github.rosemoe.sora.event.ContentChangeEvent::class.java) { _, _ ->
                if (isSettingText) return@subscribeEvent
                
                // Debounce error checking to avoid performance issues
                lifecycleScope.launch {
                    kotlinx.coroutines.delay(1000) // Wait 1 second after typing stops
                    checkForBasicErrors()
                }
            }
        }
    }
    
    private fun checkForBasicErrors() {
        try {
            val text = binding.textEdit.text.toString()
            val extension = argsFile.fileName.toString().substringAfterLast('.', "")
            
            when (extension.lowercase()) {
                "json" -> checkJsonSyntax(text)
                "xml" -> checkXmlSyntax(text)
                "java", "kt", "js", "ts" -> checkBracketMatching(text)
                // Add more language-specific checks as needed
            }
        } catch (e: Exception) {
            Log.w("TextEditor", "Error during syntax checking", e)
        }
    }
    
    // Add a public method to manually test language detection
    fun testLanguageDetection() {
        val extension = argsFile.fileName.toString().substringAfterLast('.', "")
        val scopeName = getLanguageScopeName(extension)
        Log.d("TextEditor", "TEST: File: ${argsFile.fileName}, Extension: $extension, Scope: $scopeName")
        
        // Debug: List all available grammars
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val grammarRegistry = GrammarRegistry.getInstance()
                
                // Try to get some common grammars to see what's available
                val testScopes = listOf("source.java", "source.python", "source.shell", "text.plain", "source.js")
                for (testScope in testScopes) {
                    val grammar = grammarRegistry.findGrammar(testScope)
                    Log.d("TextEditor", "Grammar test - $testScope: ${if (grammar != null) "FOUND" else "NOT FOUND"}")
                }
            } catch (e: Exception) {
                Log.e("TextEditor", "Error testing grammars", e)
            }
        }
        
        // Force re-apply language
        applyLanguage()
    }
    
    // Debug function to test asset loading
    fun debugAssetLoading() {
        lifecycleScope.launch(Dispatchers.IO) {
            val context = requireContext().applicationContext
            
            Log.d("TextEditor", "=== ASSET DEBUG TEST ===")
            
            // Test 1: Direct asset access
            try {
                val assetList = context.assets.list("textmate")
                Log.d("TextEditor", "TextMate assets found: ${assetList?.joinToString()}")
                
                val languagesStream = context.assets.open("textmate/languages.json")
                val content = languagesStream.bufferedReader().use { it.readText() }
                languagesStream.close()
                Log.d("TextEditor", "Direct access SUCCESS - languages.json size: ${content.length}")
            } catch (e: Exception) {
                Log.e("TextEditor", "Direct access FAILED", e)
            }
            
            // Test 2: FileProviderRegistry setup
            try {
                val registry = FileProviderRegistry.getInstance()
                val provider = AssetsFileResolver(context.assets)
                
                // Try to add provider
                registry.addFileProvider(provider)
                Log.d("TextEditor", "FileProvider added successfully")
                
                // Test access through registry
                val registryStream = registry.tryGetInputStream("textmate/languages.json")
                if (registryStream != null) {
                    val registryContent = registryStream.bufferedReader().use { it.readText() }
                    registryStream.close()
                    Log.d("TextEditor", "Registry access SUCCESS - size: ${registryContent.length}")
                } else {
                    Log.e("TextEditor", "Registry access FAILED - returned null")
                }
            } catch (e: Exception) {
                Log.e("TextEditor", "Registry setup FAILED", e)
            }
            
            // Test 3: Grammar loading
            try {
                val grammarRegistry = GrammarRegistry.getInstance()
                grammarRegistry.loadGrammars("textmate/languages.json")
                
                val testGrammar = grammarRegistry.findGrammar("source.python")
                Log.d("TextEditor", "Grammar loading: ${if (testGrammar != null) "SUCCESS" else "FAILED"}")
            } catch (e: Exception) {
                Log.e("TextEditor", "Grammar loading FAILED", e)
            }
            
            Log.d("TextEditor", "=== END DEBUG TEST ===")
        }
    }
    
    private fun checkJsonSyntax(text: String) {
        try {
            org.json.JSONObject(text)
        } catch (e: Exception) {
            try {
                org.json.JSONArray(text)
            } catch (e2: Exception) {
                Log.d("TextEditor", "JSON syntax error detected")
                // Could add visual indicators here
            }
        }
    }
    
    private fun checkXmlSyntax(text: String) {
        // Basic XML validation could be added here
        val openTags = text.count { it == '<' }
        val closeTags = text.count { it == '>' }
        if (openTags != closeTags) {
            Log.d("TextEditor", "XML tag mismatch detected")
        }
    }
    
    private fun checkBracketMatching(text: String) {
        var braceCount = 0
        var parenCount = 0
        var bracketCount = 0
        
        for (char in text) {
            when (char) {
                '{' -> braceCount++
                '}' -> braceCount--
                '(' -> parenCount++
                ')' -> parenCount--
                '[' -> bracketCount++
                ']' -> bracketCount--
            }
        }
        
        if (braceCount != 0 || parenCount != 0 || bracketCount != 0) {
            Log.d("TextEditor", "Bracket mismatch detected: braces=$braceCount, parens=$parenCount, brackets=$bracketCount")
        }
    }
}
