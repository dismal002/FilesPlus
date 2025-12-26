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
package org.exbin.bined.editor.android.inspector

import android.graphics.Color
import org.exbin.bined.CodeAreaCaretPosition
import org.exbin.bined.android.basic.color.BasicCodeAreaColorsProfile
import org.exbin.bined.highlight.android.SearchCodeAreaColorAssessor

/**
 * Basic values position color modifier.
 *
 * @author ExBin Project (https://exbin.org)
 */
class BasicValuesPositionColorModifier : SearchCodeAreaColorAssessor(null) {

    private var inspector: BasicValuesInspector? = null
    private var isDarkMode = false
    private var highlightColor = Color.YELLOW
    private var darkHighlightColor = Color.rgb(100, 100, 0)

    fun setInspector(inspector: BasicValuesInspector) {
        this.inspector = inspector
    }

    fun setDarkMode(darkMode: Boolean) {
        isDarkMode = darkMode
    }

    fun computeColors(caretPosition: CodeAreaCaretPosition, basicColors: BasicCodeAreaColorsProfile): BasicCodeAreaColorsProfile {
        val inspector = this.inspector ?: return basicColors
        
        val currentPosition = inspector.getCurrentPosition()
        val dataPosition = caretPosition.dataPosition
        
        // For now, just return the basic colors
        // TODO: Implement proper color modification when the API is clearer
        return basicColors
    }
}