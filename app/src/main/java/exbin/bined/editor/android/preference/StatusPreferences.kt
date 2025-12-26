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
package org.exbin.bined.editor.android.preference

import org.exbin.bined.PositionCodeType
import org.exbin.bined.editor.android.options.StatusOptions
import org.exbin.framework.bined.StatusCursorPositionFormat
import org.exbin.framework.bined.StatusDocumentSizeFormat
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Code area status panel preferences.
 *
 * @author ExBin Project (https://exbin.org)
 */
class StatusPreferences(private val preferences: Preferences) : StatusOptions {

    companion object {
        const val PREFERENCES_CURSOR_POSITION_CODE_TYPE = "statusCursorPositionFormat"
        const val PREFERENCES_CURSOR_POSITION_SHOW_OFFSET = "statusCursorShowOffset"
        const val PREFERENCES_DOCUMENT_SIZE_CODE_TYPE = "statusDocumentSizeFormat"
        const val PREFERENCES_DOCUMENT_SIZE_SHOW_RELATIVE = "statusDocumentShowRelative"
        const val PREFERENCES_OCTAL_SPACE_GROUP_SIZE = "statusOctalSpaceGroupSize"
        const val PREFERENCES_DECIMAL_SPACE_GROUP_SIZE = "statusDecimalSpaceGroupSize"
        const val PREFERENCES_HEXADECIMAL_SPACE_GROUP_SIZE = "statusHexadecimalSpaceGroupSize"
    }

    override fun getCursorPositionFormat(): StatusCursorPositionFormat {
        val cursorPositionFormat = StatusCursorPositionFormat()
        cursorPositionFormat.codeType = cursorPositionCodeType
        cursorPositionFormat.isShowOffset = isCursorShowOffset()
        return cursorPositionFormat
    }

    override fun getDocumentSizeFormat(): StatusDocumentSizeFormat {
        val documentSizeFormat = StatusDocumentSizeFormat()
        documentSizeFormat.codeType = documentSizeCodeType
        documentSizeFormat.isShowRelative = isDocumentSizeShowRelative()
        return documentSizeFormat
    }

    override fun getOctalSpaceGroupSize(): Int {
        return preferences.getInt(PREFERENCES_OCTAL_SPACE_GROUP_SIZE, 4)
    }

    override fun getDecimalSpaceGroupSize(): Int {
        return preferences.getInt(PREFERENCES_DECIMAL_SPACE_GROUP_SIZE, 3)
    }

    override fun getHexadecimalSpaceGroupSize(): Int {
        return preferences.getInt(PREFERENCES_HEXADECIMAL_SPACE_GROUP_SIZE, 4)
    }

    override fun setCursorPositionFormat(cursorPositionFormat: StatusCursorPositionFormat) {
        setCursorPositionCodeType(cursorPositionFormat.codeType)
        setCursorShowOffset(cursorPositionFormat.isShowOffset)
    }

    override fun setDocumentSizeFormat(documentSizeFormat: StatusDocumentSizeFormat) {
        setDocumentSizeCodeType(documentSizeFormat.codeType)
        setDocumentSizeShowRelative(documentSizeFormat.isShowRelative)
    }

    override fun setOctalSpaceGroupSize(octalSpaceGroupSize: Int) {
        preferences.putInt(PREFERENCES_OCTAL_SPACE_GROUP_SIZE, octalSpaceGroupSize)
    }

    override fun setDecimalSpaceGroupSize(decimalSpaceGroupSize: Int) {
        preferences.putInt(PREFERENCES_DECIMAL_SPACE_GROUP_SIZE, decimalSpaceGroupSize)
    }

    override fun setHexadecimalSpaceGroupSize(hexadecimalSpaceGroupSize: Int) {
        preferences.putInt(PREFERENCES_HEXADECIMAL_SPACE_GROUP_SIZE, hexadecimalSpaceGroupSize)
    }

    val cursorPositionCodeType: PositionCodeType
        get() {
            val defaultCodeType = PositionCodeType.DECIMAL
            return try {
                PositionCodeType.valueOf(preferences.get(PREFERENCES_CURSOR_POSITION_CODE_TYPE, defaultCodeType.name))
            } catch (ex: Exception) {
                Logger.getLogger(StatusPreferences::class.java.name).log(Level.SEVERE, null, ex)
                defaultCodeType
            }
        }

    fun setCursorPositionCodeType(statusCursorPositionCodeType: PositionCodeType) {
        preferences.put(PREFERENCES_CURSOR_POSITION_CODE_TYPE, statusCursorPositionCodeType.name)
    }

    fun isCursorShowOffset(): Boolean {
        return preferences.getBoolean(PREFERENCES_CURSOR_POSITION_SHOW_OFFSET, true)
    }

    fun setCursorShowOffset(statusCursorShowOffset: Boolean) {
        preferences.putBoolean(PREFERENCES_CURSOR_POSITION_SHOW_OFFSET, statusCursorShowOffset)
    }

    val documentSizeCodeType: PositionCodeType
        get() {
            val defaultCodeType = PositionCodeType.DECIMAL
            return try {
                PositionCodeType.valueOf(preferences.get(PREFERENCES_DOCUMENT_SIZE_CODE_TYPE, defaultCodeType.name))
            } catch (ex: Exception) {
                Logger.getLogger(StatusPreferences::class.java.name).log(Level.SEVERE, null, ex)
                defaultCodeType
            }
        }

    fun setDocumentSizeCodeType(statusDocumentSizeCodeType: PositionCodeType) {
        preferences.put(PREFERENCES_DOCUMENT_SIZE_CODE_TYPE, statusDocumentSizeCodeType.name)
    }

    fun isDocumentSizeShowRelative(): Boolean {
        return preferences.getBoolean(PREFERENCES_DOCUMENT_SIZE_SHOW_RELATIVE, true)
    }

    fun setDocumentSizeShowRelative(statusDocumentSizeShowRelative: Boolean) {
        preferences.putBoolean(PREFERENCES_DOCUMENT_SIZE_SHOW_RELATIVE, statusDocumentSizeShowRelative)
    }

    // These are now available through the interface methods
}