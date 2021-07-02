/*
 * Copyright 2021 LiteKite Startup. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.litekite.ime.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import com.litekite.ime.R
import kotlin.math.max

/**
 * A view that renders a virtual {@link Keyboard}. It handles rendering of keys and
 * detecting key presses and touch movements.
 *
 * @attr ref android.R.styleable#KeyboardView_keyBackground
 * @attr ref android.R.styleable#KeyboardView_keyPreviewLayout
 * @attr ref android.R.styleable#KeyboardView_keyPreviewOffset
 * @attr ref android.R.styleable#KeyboardView_labelTextSize
 * @attr ref android.R.styleable#KeyboardView_keyTextSize
 * @attr ref android.R.styleable#KeyboardView_keyTextColor
 * @attr ref android.R.styleable#KeyboardView_verticalCorrection
 * @attr ref android.R.styleable#KeyboardView_popupLayout
 *
 * @author Vignesh S
 * @version 1.0, 30/06/2021
 * @since 1.0
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val MAX_ALPHA = 255
    }

    private var keyBackground: Drawable? = null
    private var fontFamily: String? = null
    private var textStyle = Typeface.NORMAL
    private var keyTextSize = 18
    private var labelTextSize = 14
    private var keyTextColorPrimary = -0x1000000
    private var keyTextColorSecondary = -0x67000000

    private var keyboard: Keyboard? = null

    /** Notes if the keyboard just changed, so that we could possibly reallocate the mBuffer.  */
    private var keyboardChanged = false

    /** Whether the keyboard bitmap needs to be redrawn before it's blitted.  */
    private var drawPending = false

    /** The dirty region in the keyboard bitmap  */
    private val dirtyRect = Rect()

    /** The keyboard bitmap for faster updates  */
    private var buffer: Bitmap? = null

    /** The canvas for the above mutable keyboard bitmap  */
    private var canvas: Canvas? = null

    private val paint = Paint().apply {
        isAntiAlias = true
        textSize = keyTextSize.toFloat()
        textAlign = Paint.Align.CENTER
        alpha = MAX_ALPHA
        color = Color.TRANSPARENT
    }

    init {
        val ta = context.obtainStyledAttributes(
            attrs,
            R.styleable.KeyboardView,
            defStyleAttr,
            0
        )
        keyBackground = ta.getDrawable(
            R.styleable.KeyboardView_keyBackground
        )
        keyTextSize = ta.getDimensionPixelSize(
            R.styleable.KeyboardView_keyTextSize,
            keyTextSize
        )
        keyTextColorPrimary = ta.getColor(
            R.styleable.KeyboardView_keyTextColorPrimary,
            keyTextColorPrimary
        )
        keyTextColorSecondary = ta.getColor(
            R.styleable.KeyboardView_keyTextColorSecondary,
            keyTextColorSecondary
        )
        labelTextSize = ta.getDimensionPixelSize(
            R.styleable.KeyboardView_labelTextSize,
            labelTextSize
        )
        fontFamily = ta.getString(
            R.styleable.KeyboardView_fontFamily
        )
        textStyle = ta.getInt(
            R.styleable.KeyboardView_textStyle,
            textStyle
        )
        ta.recycle()
        // Setting typeface
        if (fontFamily == null) {
            paint.typeface = Typeface.create(Typeface.DEFAULT, textStyle)
        } else {
            paint.typeface = Typeface.create(fontFamily, textStyle)
        }
        if (isInEditMode) {
            val keyboard = Keyboard(
                context,
                resources.getIdentifier(
                    Keyboard.LAYOUT_KEYBOARD_QWERTY,
                    Keyboard.DEF_TYPE,
                    context.packageName
                )
            )
            setKeyboard(keyboard)
        }
    }

    fun setKeyboard(keyboard: Keyboard) {
        this.keyboard = keyboard
        keyboardChanged = true
    }

    private fun getLocale() = resources.configuration.locales[0]

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (keyboard == null) {
            setMeasuredDimension(
                paddingLeft + paddingRight,
                paddingTop + paddingBottom
            )
            return
        } else {
            setMeasuredDimension(
                keyboard!!.keyboardWidth + paddingLeft + paddingRight,
                keyboard!!.keyboardHeight + paddingTop + paddingBottom
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Release the buffer, if any and it will be reallocated on the next draw
        buffer = null
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (drawPending || buffer == null || keyboardChanged) {
            onBufferDraw()
        }
        buffer?.let { buffer ->
            canvas?.drawBitmap(buffer, 0F, 0F, null)
        }
    }

    private fun onBufferDraw() {
        if (keyboard == null) {
            return
        }
        if (buffer == null || keyboardChanged) {
            if (buffer == null || keyboardChanged &&
                (buffer?.width != width || buffer?.height != height)
            ) {
                // Make sure our bitmap is at least 1x1
                val width = max(1, width)
                val height = max(1, height)
                buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                canvas = Canvas(buffer!!)
            }
            invalidateAllKeys()
            keyboardChanged = false
            return
        }
        // Restrict the drawing area to dirtyRect
        canvas?.clipRect(dirtyRect)
        // Clear the clipped drawable dirtyRect before drawing
        canvas?.drawColor(0x00000000, PorterDuff.Mode.CLEAR)
        // Move the canvas coordinates to the initial position
        canvas?.translate(0F, 0F)
        // Draw background
        canvas?.drawRect(
            0F,
            0F,
            width.toFloat(),
            height.toFloat(),
            paint
        )
        // Let's draw keyboard
        keyboard?.let { it ->
            for (key in it.keys) {
                // Set drawing state for both keyBackground and keyIcon
                val drawableState = key.getDrawableState()
                keyBackground?.state = drawableState
                key.icon?.state = drawableState
                // Set keyBackground bound adjusting with key width, height
                // Both should have the same boundary area.
                val bounds = keyBackground?.bounds
                if (key.width != bounds?.right || key.height != bounds.bottom) {
                    keyBackground?.setBounds(0, 0, key.width, key.height)
                }
                // Translate the canvas coordinates to the key x, y position
                canvas?.translate((key.x + paddingLeft).toFloat(), (key.y + paddingTop).toFloat())
                // Draw the keyBackground
                canvas?.let { canvas -> keyBackground?.draw(canvas) }
                // Switch the character to uppercase if shift is pressed
                val keyLabel = key.adjustCase(getLocale()).toString()
                if (keyLabel.isEmpty()) {
                }
            }
        }
        drawPending = false
        dirtyRect.setEmpty()
    }

    /**
     * Requests a redraw of the entire keyboard. Calling [.invalidate] is not sufficient
     * because the keyboard renders the keys to an off-screen buffer and an invalidate() only
     * draws the cached buffer.
     * @see [invalidateKey]
     */
    private fun invalidateAllKeys() {
        dirtyRect.union(0, 0, width, height)
        drawPending = true
        postInvalidate()
    }

    private fun invalidateKey() {
    }
}
