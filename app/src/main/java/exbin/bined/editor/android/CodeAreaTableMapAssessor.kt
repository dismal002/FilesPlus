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

import android.content.ContentResolver
import android.net.Uri
import org.exbin.bined.CodeAreaCaretPosition
import org.exbin.bined.android.basic.color.BasicCodeAreaColorsProfile
import org.exbin.bined.highlight.android.SearchCodeAreaColorAssessor
import java.io.IOException

/**
 * Code area table map assessor.
 *
 * @author ExBin Project (https://exbin.org)
 */
class CodeAreaTableMapAssessor : SearchCodeAreaColorAssessor(null) {

    private var useTable = false
    private var tableData: ByteArray? = null

    fun setUseTable(useTable: Boolean) {
        this.useTable = useTable
    }

    fun openFile(contentResolver: ContentResolver, fileUri: Uri) {
        try {
            contentResolver.openInputStream(fileUri)?.use { inputStream ->
                tableData = inputStream.readBytes()
                useTable = true
            }
        } catch (e: IOException) {
            useTable = false
            tableData = null
        }
    }

    fun computeColors(caretPosition: CodeAreaCaretPosition, basicColors: BasicCodeAreaColorsProfile): BasicCodeAreaColorsProfile {
        if (!useTable || tableData == null) {
            return basicColors
        }

        // Apply table-based color modifications
        // This is a simplified implementation
        return basicColors
    }

    fun isUseTable(): Boolean = useTable
}