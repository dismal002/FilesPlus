package com.dismal.files.viewer.hex

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.dismal.files.R
import android.widget.OverScroller
import android.view.VelocityTracker
import android.view.ViewConfiguration
import kotlin.math.roundToInt
import org.exbin.auxiliary.binary_data.BinaryData
import org.exbin.auxiliary.binary_data.EditableBinaryData
import org.exbin.bined.CodeAreaSection
import org.exbin.bined.EditMode
import org.exbin.bined.EditOperation
import org.exbin.bined.basic.BasicCodeAreaSection
import org.exbin.bined.basic.MovementDirection
import org.exbin.bined.basic.SelectingMode
import kotlin.math.abs
import org.exbin.bined.operation.android.CodeAreaUndoRedo
import org.exbin.bined.operation.android.command.EditCharDataCommand
import org.exbin.bined.operation.android.command.EditCodeDataCommand
import org.exbin.bined.operation.android.command.EditDataCommand
import org.exbin.bined.operation.android.command.RemoveDataCommand
import org.exbin.bined.DefaultCodeAreaCaretPosition

class MaterialHexView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var binaryData: BinaryData? = null
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var undoRedo: CodeAreaUndoRedo? = null
    var internalCodeArea: org.exbin.bined.android.basic.CodeArea? = null

    // Configuration
    private var bytesPerRow = 16
    private var textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 14f * resources.displayMetrics.scaledDensity
    }
    private var rowHeight = 0f
    private var charWidth = 0f
    
    private val textColorPrimary by lazy {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
    }
    
    private val textColorSecondary by lazy {
        val typedValue = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
    }

    // Interaction State
    var caretPosition: Long = 0
        private set(value) {
            field = value.coerceIn(0, binaryData?.dataSize ?: 0)
            caretCaretOffset = 0 // Reset nibble offset on move
            onCaretMovedListener?.invoke(field)
            invalidate()
        }
    
    // Scroll state
    private var offsetX = 0f
    private var offsetY = 0f
    
    // nibble offset (0 or 1)
    private var caretCaretOffset = 0 
    
    var activeSection: CodeAreaSection = BasicCodeAreaSection.CODE_MATRIX
        set(value) {
            if (field != value) {
                field = value
                onCaretMovedListener?.invoke(caretPosition)
                invalidate()
            }
        }

    var editMode = EditMode.EXPANDING
    var editOperation = EditOperation.OVERWRITE

    private var onCaretMovedListener: ((Long) -> Unit)? = null

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        
        val metrics = textPaint.fontMetrics
        rowHeight = metrics.descent - metrics.ascent + 8 // Padding
        charWidth = textPaint.measureText("A")
    }

    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            offsetX = scroller.currX.toFloat()
            offsetY = scroller.currY.toFloat()
            
            // Re-clamp in case of overscroll
            val dataSize = binaryData?.dataSize ?: 0
            val rows = (dataSize + bytesPerRow - 1) / bytesPerRow
            val contentHeight = (rows * rowHeight).toInt() + paddingBottom + paddingTop
            val hexColStart = 10 * charWidth
            val asciiColStart = hexColStart + (bytesPerRow * 3 + 1) * charWidth
            val totalWidth = asciiColStart + bytesPerRow * charWidth
            
            offsetY = offsetY.coerceIn(0f, (contentHeight - height).coerceAtLeast(0).toFloat())
            offsetX = offsetX.coerceIn(0f, (totalWidth - width).coerceAtLeast(0f).toFloat())

            if (scroller.isFinished) {
               // scroller just finished
            } else {
               postInvalidateOnAnimation()
            }
        }
    }

    fun setOnCaretMovedListener(listener: (Long) -> Unit) {
        onCaretMovedListener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val dataSize = binaryData?.dataSize ?: 0
        val rows = (dataSize + bytesPerRow - 1) / bytesPerRow
        val desiredHeight = (rows * rowHeight).toInt() + paddingBottom + paddingTop
        val desiredWidth = MeasureSpec.getSize(widthMeasureSpec)
        
        setMeasuredDimension(
            desiredWidth,
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val data = binaryData ?: return
        val size = data.dataSize
        
        val hexColStart = 10 * charWidth // Address column
        val asciiColStart = hexColStart + (bytesPerRow * 3 + 1) * charWidth
        val totalWidth = asciiColStart + bytesPerRow * charWidth
        
        canvas.save()
        canvas.translate(-offsetX, -offsetY)
        
        val firstRow = (offsetY / rowHeight).toInt().coerceAtLeast(0)
        val visibleRows = (height / rowHeight).toInt() + 2
        
        for (i in firstRow until (firstRow + visibleRows)) {
            val position = i.toLong() * bytesPerRow
            if (position >= size) break
            
            val y = (i + 1) * rowHeight - 8
            
            // Draw Address
            textPaint.color = textColorSecondary
            canvas.drawText(String.format("%08X", position), 0f, y, textPaint)
            
            // Draw Bytes
            for (j in 0 until bytesPerRow) {
                val bytePos = position + j
                if (bytePos >= size) break
                
                val byteVal = data.getByte(bytePos).toInt() and 0xFF
                val hexX = hexColStart + (j * 3) * charWidth
                
                // Caret in Hex Section
                if (activeSection == BasicCodeAreaSection.CODE_MATRIX && bytePos == caretPosition) {
                    drawCaret(canvas, hexX + caretCaretOffset * charWidth, y)
                }
                
                textPaint.color = textColorPrimary
                canvas.drawText(String.format("%02X", byteVal), hexX, y, textPaint)
                
                // Draw ASCII
                val asciiX = asciiColStart + j * charWidth
                if (activeSection == BasicCodeAreaSection.TEXT_PREVIEW && bytePos == caretPosition) {
                    drawCaret(canvas, asciiX, y)
                }
                
                val c = if (byteVal in 32..126) byteVal.toChar() else '.'
                canvas.drawText(c.toString(), asciiX, y, textPaint)
            }
        }
        canvas.restore()
    }

    private fun drawCaret(canvas: Canvas, x: Float, y: Float) {
        val paint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.color_primary)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val rect = Rect(x.toInt(), (y - rowHeight + 12).toInt(), (x + charWidth).toInt(), (y + 4).toInt())
        canvas.drawRect(rect, paint)
    }

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isScrolling = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller.isFinished) {
                    scroller.forceFinished(true)
                }
                lastTouchX = event.x
                lastTouchY = event.y
                isScrolling = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = lastTouchX - event.x
                val dy = lastTouchY - event.y
                if (!isScrolling && (abs(dx) > 10 || abs(dy) > 10)) {
                    isScrolling = true
                }
                if (isScrolling) {
                    val data = binaryData
                    if (data != null) {
                        val dataSize = data.dataSize
                        val rows = (dataSize + bytesPerRow - 1) / bytesPerRow
                        val contentHeight = (rows * rowHeight).toInt() + paddingBottom + paddingTop
                        
                        // Calculate total width
                        val hexColStart = 10 * charWidth
                        val asciiColStart = hexColStart + (bytesPerRow * 3 + 1) * charWidth
                        val totalWidth = asciiColStart + bytesPerRow * charWidth

                        offsetX = (offsetX + dx).coerceIn(0f, (totalWidth - width).coerceAtLeast(0f).toFloat())
                        offsetY = (offsetY + dy).coerceIn(0f, (contentHeight - height).coerceAtLeast(0).toFloat())
                    }
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isScrolling) {
                     // Fling
                    velocityTracker?.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
                    val velocityX = velocityTracker?.xVelocity?.toInt() ?: 0
                    val velocityY = velocityTracker?.yVelocity?.toInt() ?: 0
                    
                    if (abs(velocityY) > minFlingVelocity || abs(velocityX) > minFlingVelocity) {
                         val data = binaryData
                         if (data != null) {
                            val dataSize = data.dataSize
                            val rows = (dataSize + bytesPerRow - 1) / bytesPerRow
                            val contentHeight = (rows * rowHeight).toInt() + paddingBottom + paddingTop
                            
                            val hexColStart = 10 * charWidth
                            val asciiColStart = hexColStart + (bytesPerRow * 3 + 1) * charWidth
                            val totalWidth = asciiColStart + bytesPerRow * charWidth
                            
                            scroller.fling(
                                offsetX.toInt(), offsetY.toInt(),
                                -velocityX, -velocityY,
                                0, (totalWidth - width).coerceAtLeast(0f).toInt(),
                                0, (contentHeight - height).coerceAtLeast(0).toInt()
                            )
                            postInvalidateOnAnimation()
                         }
                    }
                } else if (event.action == MotionEvent.ACTION_UP) {
                    requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    
                    // Simple hit testing with scroll offset
                    val hexColStart = 10 * charWidth
                    val asciiColStart = hexColStart + (bytesPerRow * 3 + 1) * charWidth
                    
                    val worldX = event.x + offsetX
                    val worldY = event.y + offsetY
                    
                    val row = (worldY / rowHeight).toInt()
                    
                    if (worldX >= hexColStart && worldX < asciiColStart) {
                        activeSection = BasicCodeAreaSection.CODE_MATRIX
                        val col = ((worldX - hexColStart) / (charWidth * 3)).toInt()
                        caretPosition = (row * bytesPerRow + col).toLong()
                    } else if (worldX >= asciiColStart) {
                        activeSection = BasicCodeAreaSection.TEXT_PREVIEW
                        val col = ((worldX - asciiColStart) / charWidth).toInt()
                        caretPosition = (row * bytesPerRow + col).toLong()
                    }
                }
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return true
    }

    fun move(direction: MovementDirection) {
        when (direction) {
            MovementDirection.LEFT -> {
                if (caretCaretOffset > 0) {
                    caretCaretOffset = 0
                } else if (caretPosition > 0) {
                    caretPosition--
                    if (activeSection == BasicCodeAreaSection.CODE_MATRIX) caretCaretOffset = 1
                }
            }
            MovementDirection.RIGHT -> {
                if (activeSection == BasicCodeAreaSection.CODE_MATRIX && caretCaretOffset == 0) {
                    caretCaretOffset = 1
                } else {
                    caretPosition++
                    caretCaretOffset = 0
                }
            }
            MovementDirection.UP -> caretPosition -= bytesPerRow
            MovementDirection.DOWN -> caretPosition += bytesPerRow
            MovementDirection.ROW_START -> caretPosition = (caretPosition / bytesPerRow) * bytesPerRow
            MovementDirection.ROW_END -> caretPosition = (caretPosition / bytesPerRow) * bytesPerRow + (bytesPerRow - 1)
            MovementDirection.DOC_START -> caretPosition = 0
            MovementDirection.DOC_END -> caretPosition = binaryData?.dataSize ?: 0
            else -> {}
        }
        invalidate()
        scrollToCaret()
    }

    fun toggleEditOperation() {
        editOperation = if (editOperation == EditOperation.OVERWRITE) EditOperation.INSERT else EditOperation.OVERWRITE
        onEditOperationChangedListener?.invoke(editOperation)
        invalidate()
    }
    
    private var onEditOperationChangedListener: ((EditOperation) -> Unit)? = null
    fun setOnEditOperationChangedListener(listener: (EditOperation) -> Unit) {
        onEditOperationChangedListener = listener
    }

    fun typeChar(c: Char) {
        val data = binaryData as? EditableBinaryData ?: return
        val ur = undoRedo ?: return
        
        val op = if (editOperation == EditOperation.INSERT) 
            EditDataCommand.EditOperationType.INSERT 
        else 
            EditDataCommand.EditOperationType.OVERWRITE
            
        if (activeSection == BasicCodeAreaSection.CODE_MATRIX) {
            val digit = Character.digit(c, 16)
            if (digit == -1) return
            
            val currentByte = if (caretPosition < data.dataSize) data.getByte(caretPosition).toInt() else 0
            val newByte = if (caretCaretOffset == 0) {
                (digit shl 4) or (currentByte and 0x0F)
            } else {
                (currentByte and 0xF0) or digit
            }
            
            val command = EditCodeDataCommand(internalCodeArea!!, op, caretPosition, caretCaretOffset, newByte.toByte())
            ur.execute(command)
            
            if (caretCaretOffset == 1) {
                caretPosition++
                caretCaretOffset = 0
            } else {
                caretCaretOffset = 1
            }
        } else {
            val command = EditCharDataCommand(internalCodeArea!!, op, caretPosition, c)
            ur.execute(command)
            caretPosition++
        }
        invalidate()
        scrollToCaret()
    }
    
    fun backspace() {
        val data = binaryData as? EditableBinaryData ?: return
        val ur = undoRedo ?: return
        
        if (caretPosition <= 0 && caretCaretOffset == 0) return
        
        if (activeSection == BasicCodeAreaSection.CODE_MATRIX) {
            if (caretCaretOffset == 1) {
                caretCaretOffset = 0
            } else {
                caretPosition--
                caretCaretOffset = 1
            }
            // In hex mode, backspace typically just moves the caret back.
            // Some editors zero out the byte. We'll stick to movement for now
            // to be safe, as "deleting" a nibble is ambiguous.
        } else {
            caretPosition--
            val command = RemoveDataCommand(internalCodeArea!!, caretPosition, 0, 1L)
            ur.execute(command)
        }
        invalidate()
        scrollToCaret()
    }

    fun deleteData() {
         val data = binaryData as? EditableBinaryData ?: return
         val ur = undoRedo ?: return
         if (caretPosition >= data.dataSize) return
         
         val command = RemoveDataCommand(internalCodeArea!!, caretPosition, 0, 1L)
         ur.execute(command)
         invalidate()
         scrollToCaret()
    }

    private fun scrollToCaret() {
        val row = (caretPosition / bytesPerRow).toInt()
        val hexColStart = 10 * charWidth
        val asciiColStart = hexColStart + (bytesPerRow * 3 + 1) * charWidth
        
        val caretX = if (activeSection == BasicCodeAreaSection.CODE_MATRIX) {
            hexColStart + (caretPosition % bytesPerRow) * 3 * charWidth + caretCaretOffset * charWidth
        } else {
            asciiColStart + (caretPosition % bytesPerRow) * charWidth
        }
        
        val caretY = row * rowHeight
        
        // Ensure visible vertically
        if (caretY < offsetY) {
            offsetY = caretY
        } else if (caretY + rowHeight > offsetY + height) {
            offsetY = (caretY + rowHeight - height).coerceAtLeast(0f)
        }
        
        // Ensure visible horizontally
        // Ensure visible horizontally
        if (caretX < offsetX) {
            offsetX = caretX
        } else if (caretX + charWidth > offsetX + width) {
            offsetX = (caretX + charWidth - width).coerceAtLeast(0f)
        }
        
        if (!scroller.isFinished) {
            scroller.abortAnimation()
        }
        
        invalidate()
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                for (c in text) {
                    typeChar(c)
                }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DEL -> {
                            if (caretPosition > 0) {
                                caretPosition--
                                // Implementation of backspace via command...
                                invalidate()
                            }
                            return true
                        }
                    }
                }
                return super.sendKeyEvent(event)
            }
        }
    }
    
    // State accessors for fragment
    fun getCaretPositionValue(): Long = caretPosition
    
    fun getCodeOffsetValue(): Int = caretCaretOffset

    fun getActiveSectionValue(): CodeAreaSection = activeSection
    
    fun getEditModeValue(): EditMode = editMode
    
    fun setEditModeValue(mode: EditMode) {
        this.editMode = mode
    }
    
    fun getEditOperationValue(): EditOperation = editOperation
    
    fun setEditOperationValue(operation: EditOperation) {
        this.editOperation = operation
    }

    fun jumpToPosition(position: Long) {
        this.caretPosition = position
        scrollToCaret()
    }

    fun findNext(pattern: ByteArray, wrapAround: Boolean = true): Boolean {
        val data = binaryData ?: return false
        val size = data.dataSize
        if (pattern.isEmpty() || pattern.size > size) return false

        // Search from caret + 1 to end
        val start = (caretPosition + 1).coerceAtMost(size)
        val foundEnd = findPattern(data, pattern, start, size)
        if (foundEnd != -1L) {
            jumpToPosition(foundEnd)
            return true
        }

        // Search from start to current caret if wrapAround is enabled
        if (wrapAround) {
            val foundStart = findPattern(data, pattern, 0, caretPosition)
            if (foundStart != -1L) {
                jumpToPosition(foundStart)
                return true
            }
        }

        return false
    }

    private fun findPattern(data: BinaryData, pattern: ByteArray, from: Long, to: Long): Long {
        if (to - from < pattern.size) return -1L
        for (i in from until (to - pattern.size + 1)) {
            var match = true
            for (j in pattern.indices) {
                if (data.getByte(i + j) != pattern[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1L
    }
}
