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
import org.exbin.bined.CharsetStreamTranslator
import org.exbin.bined.CodeAreaUtils
import org.exbin.bined.android.CodeAreaAndroidUtils
import org.exbin.bined.android.basic.CodeArea
import org.exbin.bined.android.capability.ColorAssessorPainterCapable
import org.exbin.bined.highlight.android.SearchCodeAreaColorAssessor
import org.exbin.bined.highlight.android.SearchMatch
import java.nio.charset.Charset
import java.nio.charset.CharsetEncoder
import java.util.*

/**
 * Binary search service.
 *
 * @author ExBin Project (https://exbin.org)
 */
class BinarySearchServiceImpl(private val codeArea: CodeArea) : BinarySearchService {

    companion object {
        private const val MAX_MATCHES_COUNT = 100
    }

    private val lastSearchParametersInternal = SearchParameters()

    override val lastSearchParameters: SearchParameters
        get() = lastSearchParametersInternal

    override fun performFind(searchParameters: SearchParameters, searchStatusListener: BinarySearchService.SearchStatusListener?) {
        val searchAssessor = CodeAreaAndroidUtils.findColorAssessor(
            codeArea.painter as ColorAssessorPainterCapable,
            SearchCodeAreaColorAssessor::class.java
        )
        val condition = searchParameters.condition
        searchStatusListener?.clearStatus()
        
        if (condition.isEmpty()) {
            searchAssessor?.clearMatches()
            codeArea.repaint()
            return
        }

        val position = when (searchParameters.searchDirection) {
            SearchParameters.SearchDirection.FORWARD -> {
                if (searchParameters.isSearchFromCursor) {
                    codeArea.activeCaretPosition.dataPosition
                } else {
                    0L
                }
            }
            SearchParameters.SearchDirection.BACKWARD -> {
                if (searchParameters.isSearchFromCursor) {
                    codeArea.activeCaretPosition.dataPosition - 1
                } else {
                    val searchDataSize = when (condition.searchMode) {
                        SearchCondition.SearchMode.TEXT -> condition.searchText.length.toLong()
                        SearchCondition.SearchMode.BINARY -> condition.getBinaryData()?.dataSize ?: 0L
                        else -> throw CodeAreaUtils.getInvalidTypeException(condition.searchMode)
                    }
                    codeArea.dataSize - searchDataSize
                }
            }
            else -> throw CodeAreaUtils.getInvalidTypeException(searchParameters.searchDirection)
        }
        
        searchParameters.startPosition = position
        lastSearchParametersInternal.setFromParameters(searchParameters)

        // Perform the actual search
        performSearchOperation(searchParameters, searchStatusListener)
    }

    override fun setMatchPosition(matchPosition: Int) {
        val searchAssessor = CodeAreaAndroidUtils.findColorAssessor(
            codeArea.painter as ColorAssessorPainterCapable,
            SearchCodeAreaColorAssessor::class.java
        )
        searchAssessor?.let { assessor ->
            assessor.currentMatchIndex = matchPosition
            codeArea.repaint()
        }
    }

    override fun performFindAgain(searchStatusListener: BinarySearchService.SearchStatusListener?) {
        performFind(lastSearchParametersInternal, searchStatusListener)
    }

    override fun performReplace(searchParameters: SearchParameters, replaceParameters: ReplaceParameters) {
        // Basic replace implementation
        // This would need to be expanded for full functionality
    }

    override fun clearMatches() {
        val searchAssessor = CodeAreaAndroidUtils.findColorAssessor(
            codeArea.painter as ColorAssessorPainterCapable,
            SearchCodeAreaColorAssessor::class.java
        )
        searchAssessor?.let {
            it.clearMatches()
        }
        codeArea.repaint()
    }

    private fun performSearchOperation(searchParameters: SearchParameters, searchStatusListener: BinarySearchService.SearchStatusListener?) {
        val condition = searchParameters.condition
        val matches = mutableListOf<SearchMatch>()
        
        // Basic search implementation
        when (condition.searchMode) {
            SearchCondition.SearchMode.TEXT -> {
                performTextSearch(condition.searchText, searchParameters, matches)
            }
            SearchCondition.SearchMode.BINARY -> {
                condition.getBinaryData()?.let { binaryData ->
                    performBinarySearch(binaryData, searchParameters, matches)
                }
            }
        }

        // Update search results
        val searchAssessor = CodeAreaAndroidUtils.findColorAssessor(
            codeArea.painter as ColorAssessorPainterCapable,
            SearchCodeAreaColorAssessor::class.java
        )
        searchAssessor?.let { assessor ->
            assessor.setMatches(matches)
            
            if (matches.isNotEmpty()) {
                assessor.currentMatchIndex = 0
                val foundMatches = BinarySearchService.FoundMatches(matches.size, 0)
                searchStatusListener?.setStatus(foundMatches, searchParameters.matchMode)
            } else {
                val foundMatches = BinarySearchService.FoundMatches(0, -1)
                searchStatusListener?.setStatus(foundMatches, searchParameters.matchMode)
            }
        }
        
        codeArea.repaint()
    }

    private fun performTextSearch(searchText: String, searchParameters: SearchParameters, matches: MutableList<SearchMatch>) {
        if (searchText.isEmpty()) return
        
        val data = codeArea.contentData
        val charset = codeArea.charset
        val encoder = charset.newEncoder()
        
        try {
            val searchBytes = searchText.toByteArray(charset)
            performBinaryDataSearch(data, searchBytes, searchParameters, matches)
        } catch (e: Exception) {
            // Handle encoding errors
        }
    }

    private fun performBinarySearch(binaryData: BinaryData, searchParameters: SearchParameters, matches: MutableList<SearchMatch>) {
        val data = codeArea.contentData
        val searchBytes = ByteArray(binaryData.dataSize.toInt())
        binaryData.copyToArray(0, searchBytes, 0, searchBytes.size)
        performBinaryDataSearch(data, searchBytes, searchParameters, matches)
    }

    private fun performBinaryDataSearch(data: BinaryData, searchBytes: ByteArray, searchParameters: SearchParameters, matches: MutableList<SearchMatch>) {
        if (searchBytes.isEmpty()) return
        
        val dataSize = data.dataSize
        val searchLength = searchBytes.size
        var position = searchParameters.startPosition
        
        when (searchParameters.searchDirection) {
            SearchParameters.SearchDirection.FORWARD -> {
                while (position <= dataSize - searchLength && matches.size < MAX_MATCHES_COUNT) {
                    if (matchesAtPosition(data, position, searchBytes, searchParameters.isMatchCase)) {
                        matches.add(SearchMatch(position, searchLength.toLong()))
                        if (searchParameters.matchMode == SearchParameters.MatchMode.SINGLE) {
                            break
                        }
                    }
                    position++
                }
            }
            SearchParameters.SearchDirection.BACKWARD -> {
                while (position >= 0 && matches.size < MAX_MATCHES_COUNT) {
                    if (matchesAtPosition(data, position, searchBytes, searchParameters.isMatchCase)) {
                        matches.add(0, SearchMatch(position, searchLength.toLong()))
                        if (searchParameters.matchMode == SearchParameters.MatchMode.SINGLE) {
                            break
                        }
                    }
                    position--
                }
            }
        }
    }

    private fun matchesAtPosition(data: BinaryData, position: Long, searchBytes: ByteArray, matchCase: Boolean): Boolean {
        if (position + searchBytes.size > data.dataSize) return false
        
        for (i in searchBytes.indices) {
            val dataByte = data.getByte(position + i)
            val searchByte = searchBytes[i]
            
            if (matchCase) {
                if (dataByte != searchByte) return false
            } else {
                // Case-insensitive comparison for text
                val dataChar = dataByte.toInt().toChar().lowercaseChar()
                val searchChar = searchByte.toInt().toChar().lowercaseChar()
                if (dataChar != searchChar) return false
            }
        }
        return true
    }
}