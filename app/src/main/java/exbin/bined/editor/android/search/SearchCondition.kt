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

import org.exbin.auxiliary.binary_data.BinaryData
import org.exbin.auxiliary.binary_data.EditableBinaryData
import org.exbin.auxiliary.binary_data.jna.JnaBufferEditableData
import org.exbin.bined.CodeAreaUtils
import java.util.*

/**
 * Parameters for action to search for occurrences of text or data.
 *
 * @author ExBin Project (https://exbin.org)
 */
class SearchCondition {

    var searchMode = SearchMode.TEXT
    var searchText = ""
    private var _binaryData: EditableBinaryData? = null

    constructor()

    /**
     * This is copy constructor.
     *
     * @param source source condition
     */
    constructor(source: SearchCondition) {
        searchMode = source.searchMode
        searchText = source.searchText
        _binaryData = JnaBufferEditableData()
        source._binaryData?.let { sourceBinaryData ->
            _binaryData?.insert(0, sourceBinaryData)
        }
    }

    fun getBinaryData(): BinaryData? = _binaryData

    fun setBinaryData(binaryData: EditableBinaryData?) {
        this._binaryData = binaryData
    }

    fun isEmpty(): Boolean {
        return when (searchMode) {
            SearchMode.TEXT -> searchText.isEmpty()
            SearchMode.BINARY -> _binaryData == null || _binaryData!!.isEmpty
            else -> throw CodeAreaUtils.getInvalidTypeException(searchMode)
        }
    }

    override fun hashCode(): Int = 3

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        
        val otherCondition = other as SearchCondition
        if (searchMode != otherCondition.searchMode) return false
        
        return when (searchMode) {
            SearchMode.TEXT -> searchText == otherCondition.searchText
            SearchMode.BINARY -> _binaryData == otherCondition._binaryData
            else -> false
        }
    }

    fun clear() {
        searchText = ""
        _binaryData?.clear()
    }

    enum class SearchMode {
        TEXT, BINARY
    }
}