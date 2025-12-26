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
 * Binary search.
 *
 * @author ExBin Project (https://exbin.org)
 */
class BinarySearch {

    companion object {
        private const val DEFAULT_DELAY = 500
    }

    private var invokeSearchThread: InvokeSearchThread? = null
    private var searchThread: SearchThread? = null

    private var currentSearchOperation = SearchOperation.FIND
    private var currentSearchDirection = SearchParameters.SearchDirection.FORWARD
    private val currentSearchParameters = SearchParameters()
    private val currentReplaceParameters = ReplaceParameters()

    private var panelClosingListener: PanelClosingListener? = null
    var binarySearchService: BinarySearchService? = null
    private var searchStatusListener: BinarySearchService.SearchStatusListener? = null

    fun setPanelClosingListener(panelClosingListener: PanelClosingListener?) {
        this.panelClosingListener = panelClosingListener
    }

    fun getSearchStatusListener(): BinarySearchService.SearchStatusListener? = searchStatusListener

    private fun invokeSearch(searchOperation: SearchOperation) {
        invokeSearch(searchOperation, currentSearchParameters, currentReplaceParameters, 0)
    }

    private fun invokeSearch(searchOperation: SearchOperation, delay: Int) {
        invokeSearch(searchOperation, currentSearchParameters, currentReplaceParameters, delay)
    }

    private fun invokeSearch(searchOperation: SearchOperation, searchParameters: SearchParameters, replaceParameters: ReplaceParameters?) {
        invokeSearch(searchOperation, searchParameters, replaceParameters, 0)
    }

    private fun invokeSearch(searchOperation: SearchOperation, searchParameters: SearchParameters, replaceParameters: ReplaceParameters?, delay: Int) {
        invokeSearchThread?.interrupt()
        invokeSearchThread = InvokeSearchThread().apply {
            this.delay = delay
        }
        currentSearchOperation = searchOperation
        currentSearchParameters.setFromParameters(searchParameters)
        replaceParameters?.let { currentReplaceParameters.setFromParameters(it) }
        invokeSearchThread?.start()
    }

    // TODO Move to search panel
    fun performFind(searchParameters: SearchParameters, searchStatusListener: BinarySearchService.SearchStatusListener) {
        this.searchStatusListener = searchStatusListener
        invokeSearch(SearchOperation.FIND, searchParameters, null, 0)
    }

    // TODO Move to search panel
    fun performFindAgain(searchStatusListener: BinarySearchService.SearchStatusListener) {
        this.searchStatusListener = searchStatusListener
        invokeSearch(SearchOperation.FIND_AGAIN, currentSearchParameters, currentReplaceParameters, 0)
    }

    fun cancelSearch() {
        invokeSearchThread?.interrupt()
        searchThread?.interrupt()
    }

    fun clearSearch() {
        val condition = currentSearchParameters.condition
        condition.clear()
        binarySearchService?.clearMatches()
        searchStatusListener?.clearStatus()
    }

    fun dataChanged() {
        binarySearchService?.clearMatches()
        invokeSearch(currentSearchOperation, DEFAULT_DELAY)
    }

    private inner class InvokeSearchThread : Thread("InvokeSearchThread") {
        var delay = DEFAULT_DELAY

        override fun run() {
            try {
                sleep(delay.toLong())
                searchThread?.interrupt()
                searchThread = SearchThread()
                searchThread?.start()
            } catch (ex: InterruptedException) {
                // don't search
            }
        }
    }

    private inner class SearchThread : Thread("SearchThread") {
        override fun run() {
            when (currentSearchOperation) {
                SearchOperation.FIND -> {
                    binarySearchService?.performFind(currentSearchParameters, searchStatusListener)
                }
                SearchOperation.FIND_AGAIN -> {
                    binarySearchService?.performFindAgain(searchStatusListener)
                }
                SearchOperation.REPLACE -> {
                    binarySearchService?.performReplace(currentSearchParameters, currentReplaceParameters)
                }
                else -> throw UnsupportedOperationException("Not supported yet.")
            }
        }
    }

    interface PanelClosingListener {
        fun closed()
    }

    private enum class SearchOperation {
        FIND,
        FIND_AGAIN,
        REPLACE,
        REPLACE_ALL
    }
}