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

/**
 * Binary search service.
 *
 * @author ExBin Project (https://exbin.org)
 */
interface BinarySearchService {

    fun performFind(dialogSearchParameters: SearchParameters, searchStatusListener: SearchStatusListener?)

    fun setMatchPosition(matchPosition: Int)

    fun performFindAgain(searchStatusListener: SearchStatusListener?)

    fun performReplace(searchParameters: SearchParameters, replaceParameters: ReplaceParameters)

    val lastSearchParameters: SearchParameters

    fun clearMatches()

    interface SearchStatusListener {
        fun setStatus(foundMatches: FoundMatches, matchMode: SearchParameters.MatchMode)
        fun clearStatus()
    }

    class FoundMatches {
        var matchesCount: Int = 0
        var matchPosition: Int = -1

        constructor() {
            matchesCount = 0
            matchPosition = -1
        }

        constructor(matchesCount: Int, matchPosition: Int) {
            if (matchPosition >= matchesCount) {
                throw IllegalStateException("Match position is out of range")
            }
            this.matchesCount = matchesCount
            this.matchPosition = matchPosition
        }

        fun next() {
            if (matchPosition == matchesCount - 1) {
                throw IllegalStateException("Cannot find next on last match")
            }
            matchPosition++
        }

        fun prev() {
            if (matchPosition == 0) {
                throw IllegalStateException("Cannot find previous on first match")
            }
            matchPosition--
        }
    }
}