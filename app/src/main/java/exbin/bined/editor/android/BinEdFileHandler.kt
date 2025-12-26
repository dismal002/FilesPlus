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
import org.exbin.auxiliary.binary_data.BinaryData
import org.exbin.auxiliary.binary_data.EditableBinaryData
import org.exbin.auxiliary.binary_data.delta.DeltaDocument
import org.exbin.auxiliary.binary_data.delta.SegmentsRepository
import org.exbin.auxiliary.binary_data.jna.JnaBufferEditableData
import org.exbin.auxiliary.binary_data.jna.paged.JnaBufferPagedData
import org.exbin.auxiliary.binary_data.paged.PagedData
import org.exbin.bined.EditOperation
import org.exbin.bined.android.CodeAreaPainter
import org.exbin.bined.android.basic.CodeArea
import org.exbin.bined.android.capability.CharAssessorPainterCapable
import org.exbin.bined.android.capability.ColorAssessorPainterCapable
import org.exbin.bined.operation.android.CodeAreaOperationCommandHandler
import org.exbin.bined.operation.android.CodeAreaUndoRedo
import org.exbin.framework.bined.BinEdCodeAreaAssessor
import org.exbin.framework.bined.FileHandlingMode
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * File handler for binary editor.
 *
 * @author ExBin Project (https://exbin.org)
 */
class BinEdFileHandler(private var codeAreaInstance: CodeArea) {

    lateinit var segmentsRepository: SegmentsRepository
    private var myUndoRedo: CodeAreaUndoRedo? = null
    private var myCodeAreaAssessor: BinEdCodeAreaAssessor? = null
    private val codeAreaTableMapAssessorInstance = CodeAreaTableMapAssessor()

    private var documentOriginalSizeValue = 0L
    private var currentFileUriValue: Uri? = null
    private var pickerInitialUriValue: Uri? = null

    init {
        initializeComponents()
    }

    private fun initializeComponents() {
        codeAreaInstance.setContentData(JnaBufferEditableData())
        codeAreaInstance.setEditOperation(EditOperation.INSERT)
        
        myUndoRedo = CodeAreaUndoRedo(codeAreaInstance)

        val commandHandler = CodeAreaOperationCommandHandler(codeAreaInstance.context, codeAreaInstance, myUndoRedo!!)
        codeAreaInstance.setCommandHandler(commandHandler)
        
        val painter = codeAreaInstance.painter
        myCodeAreaAssessor = BinEdCodeAreaAssessor(
            (painter as ColorAssessorPainterCapable).colorAssessor,
            null
        )
        (painter as ColorAssessorPainterCapable).colorAssessor = myCodeAreaAssessor!!
        codeAreaInstance.painter = painter
    }

    fun setNewData(fileHandlingMode: FileHandlingMode) {
        val newData = when (fileHandlingMode) {
            FileHandlingMode.DELTA -> segmentsRepository.createDocument()
            else -> JnaBufferPagedData()
        }
        codeAreaInstance.setContentData(newData)

        myUndoRedo?.clear()
        currentFileUriValue = null
        documentOriginalSizeValue = 0
    }

    fun openFile(contentResolver: ContentResolver, fileUri: Uri, fileHandlingMode: FileHandlingMode) {
        val oldData = codeAreaInstance.contentData
        try {
            when (fileHandlingMode) {
                FileHandlingMode.DELTA -> {
                    // Close previous data source if compatible
                    if (oldData is DeltaDocument) {
                        val oldSource = oldData.dataSource
                        if (oldSource is ContentDataSource) {
                            oldSource.close()
                        }
                    }

                    val dataSource = ContentDataSource(contentResolver, fileUri)
                    segmentsRepository.addDataSource(dataSource)
                    val document = segmentsRepository.createDocument(dataSource)
                    
                    codeAreaInstance.setContentData(document)
                    oldData.dispose()
                    myUndoRedo?.clear()
                    currentFileUriValue = fileUri
                    pickerInitialUriValue = fileUri
                    fileSync()
                }
                else -> {
                    var data = oldData
                    if (data !is PagedData) {
                        data = JnaBufferPagedData()
                        // oldData disposed in post block to avoid race if it was used
                    }
                    val inputStream = contentResolver.openInputStream(fileUri)
                    if (inputStream != null) {
                        (data as EditableBinaryData).loadFromStream(inputStream)
                        inputStream.close()
                        
                        if (data !== oldData) {
                            oldData.dispose()
                        }
                        codeAreaInstance.setContentData(data)
                        myUndoRedo?.clear()
                        currentFileUriValue = fileUri
                        pickerInitialUriValue = fileUri
                        fileSync()
                    }
                }
            }
        } catch (ex: IOException) {
            Logger.getLogger(BinEdFileHandler::class.java.name).log(Level.SEVERE, null, ex)
        }
    }

    fun saveFile(contentResolver: ContentResolver, fileUri: Uri) {
        val contentData = codeAreaInstance.contentData
        try {
            when (contentData) {
                is DeltaDocument -> {
                    var fileSource = contentData.dataSource as? ContentDataSource
                    if (fileSource == null || fileUri != fileSource.fileUri) {
                        fileSource = ContentDataSource(contentResolver, fileUri)
                        segmentsRepository.addDataSource(fileSource)
                        contentData.dataSource = fileSource
                    }
                    segmentsRepository.saveDocument(contentData)

                    fileSync()
                    currentFileUriValue = fileUri
                    pickerInitialUriValue = fileUri
                }
                else -> {
                    val outputStream = contentResolver.openOutputStream(fileUri) ?: return
                    contentData.saveToStream(outputStream)
                    outputStream.close()

                    fileSync()
                    currentFileUriValue = fileUri
                    pickerInitialUriValue = fileUri
                }
            }
        } catch (ex: Exception) {
            Logger.getLogger(BinEdFileHandler::class.java.name).log(Level.SEVERE, "Save failed", ex)
            throw RuntimeException(ex.message ?: ex.toString(), ex)
        }
    }

    private fun fileSync() {
        documentOriginalSizeValue = codeAreaInstance.dataSize
        myUndoRedo?.setSyncPosition()
    }

    val codeAreaTableMapAssessor: CodeAreaTableMapAssessor
        get() = codeAreaTableMapAssessorInstance

    val codeArea: CodeArea
        get() = codeAreaInstance

    val undoRedo: CodeAreaUndoRedo
        get() = myUndoRedo!!

    val codeAreaAssessor: BinEdCodeAreaAssessor
        get() = myCodeAreaAssessor!!

    val documentOriginalSize: Long
        get() = documentOriginalSizeValue

    val fileHandlingMode: FileHandlingMode
        get() = if (codeAreaInstance.contentData is DeltaDocument) FileHandlingMode.DELTA else FileHandlingMode.MEMORY

    val currentFileUri: Uri?
        get() = currentFileUriValue

    val pickerInitialUri: Uri?
        get() = pickerInitialUriValue

    fun clearFileUri() {
        currentFileUriValue = null
        pickerInitialUriValue = null
    }

    val isModified: Boolean
        get() = myUndoRedo?.isModified ?: false
}