/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package com.dismal.files.filelist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.dismal.files.app.AppActivity
import com.dismal.files.file.MimeType
import com.dismal.files.file.fileProviderUri
import com.dismal.files.util.ParcelableArgs
import com.dismal.files.util.ParcelableParceler
import com.dismal.files.util.args
import com.dismal.files.util.showToast

class HexEditorActivity : AppActivity() {
    private val args by args<Args>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = args.path
        openInHexEditor(path)
        finish()
    }

    private fun openInHexEditor(path: Path) {
        try {
            // First try to launch the standalone hex editor app
            val hexEditorIntent = Intent().apply {
                action = Intent.ACTION_VIEW
                setClassName(
                    "org.exbin.bined.editor.android",
                    "org.exbin.bined.editor.android.MainActivity"
                )
                data = path.fileProviderUri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            
            startActivity(hexEditorIntent)
            
        } catch (e: Exception) {
            // If the hex editor app is not installed, try to open with any available hex editor
            try {
                val genericIntent = Intent().apply {
                    action = Intent.ACTION_VIEW
                    data = path.fileProviderUri
                    type = "application/octet-stream"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                
                val chooser = Intent.createChooser(genericIntent, "Open with Hex Editor")
                startActivity(chooser)
                
            } catch (e2: Exception) {
                // If no hex editor is available, show a message with instructions
                showToast("No hex editor found. Please install a hex editor app from the Play Store.")
            }
        }
    }

    @Parcelize
    class Args(
        val path: @WriteWith<ParcelableParceler> Path, 
        val mimeType: MimeType
    ) : ParcelableArgs

    companion object {
        fun createIntent(): Intent = Intent(Intent.ACTION_VIEW)
            .setClass(com.dismal.files.app.application, HexEditorActivity::class.java)
    }
}