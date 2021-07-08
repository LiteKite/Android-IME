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
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.litekite.ime.R
import com.litekite.ime.app.ImeApp
import com.litekite.ime.util.ContextUtil.themeContext
import com.litekite.ime.util.StringUtil.isPunctuation
import kotlin.math.max

/**
 * A view that renders a virtual {@link Keyboard}. It handles rendering of keys and
 * detecting key presses and touch movements.
 *
 * @attr ref R.styleable#KeyboardView_keyBackground
 * @attr ref R.styleable#KeyboardView_keyPreviewLayout
 * @attr ref R.styleable#KeyboardView_keyPreviewOffset
 * @attr ref R.styleable#KeyboardView_labelTextSize
 * @attr ref R.styleable#KeyboardView_keyTextSize
 * @attr ref R.styleable#KeyboardView_keyTextColor
 * @attr ref R.styleable#KeyboardView_popupLayout
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
        private val TAG: String = KeyboardView::class.java.simpleName
        private const val SCRIM_ALPHA = 242 // 95% opacity.
        private const val MAX_ALPHA = 255
    }

    private var scrimColor: Int
    private var scrimAlpha = 0
    private val keyBackground: Drawable?
    private val fontFamily: String?
    private var textStyle = Typeface.NORMAL
    private var keyTextSize = 18
    private var labelTextSize = 14
    private val keyPunctuationSize: Int
    private var keyTextColorPrimary = -0x1000000
    private val useKeyTextColorSecondary: Boolean
    private var keyTextColorSecondary = -0x67000000

    private var keyboard: Keyboard? = null

    /** Notes if the keyboard just changed, so that we could possibly reallocate the buffer.  */
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
        val color = ContextCompat.getColor(context, R.color.keyboard_scrim_color)
        scrimColor = Color.argb(
            SCRIM_ALPHA,
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
        keyBackground = ta.getDrawable(
            R.styleable.KeyboardView_keyBackground
        )
        keyTextColorPrimary = ta.getColor(
            R.styleable.KeyboardView_keyTextColorPrimary,
            keyTextColorPrimary
        )
        useKeyTextColorSecondary = ta.getBoolean(
            R.styleable.KeyboardView_useKeyTextColorSecondary,
            false
        )
        keyTextColorSecondary = ta.getColor(
            R.styleable.KeyboardView_keyTextColorSecondary,
            keyTextColorSecondary
        )
        keyTextSize = ta.getDimensionPixelSize(
            R.styleable.KeyboardView_keyTextSize,
            keyTextSize
        )
        labelTextSize = ta.getDimensionPixelSize(
            R.styleable.KeyboardView_labelTextSize,
            labelTextSize
        )
        keyPunctuationSize = resources.getDimensionPixelSize(
            R.dimen.keyboard_view_key_punctuation_height
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
                context.themeContext(),
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
        invalidateAllKeys()
    }

    private fun getLocale() = resources.configuration.locales[0]

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val keyboard = this.keyboard
        if (keyboard == null) {
            setMeasuredDimension(
                paddingLeft + paddingRight,
                paddingTop + paddingBottom
            )
            return
        } else {
            setMeasuredDimension(
                keyboard.keyboardWidth + paddingLeft + paddingRight,
                keyboard.keyboardHeight + paddingTop + paddingBottom
            )
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateAllKeys()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (drawPending || buffer == null || keyboardChanged) {
            onBufferDraw(null)
        }
        val buffer = this.buffer
        if (buffer != null) {
            canvas.drawBitmap(buffer, 0F, 0F, null)
        }
    }

    private fun onBufferDraw(invalidatedKey: Keyboard.Key?) {
        if (buffer == null || keyboardChanged) {
            if (buffer == null || keyboardChanged &&
                (buffer!!.width != width || buffer!!.height != height)
            ) {
                // Make sure our bitmap is at least 1x1
                val width = max(1, width)
                val height = max(1, height)
                buffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                canvas = Canvas(buffer!!)
            }
            keyboardChanged = false
        } else {
            canvas?.setBitmap(buffer)
        }
        val keyboard = this.keyboard ?: return
        val canvas = this.canvas ?: return
        // Restrict the drawing area to dirtyRect
        canvas.clipRect(dirtyRect)
        // Clear the clipped drawable dirtyRect before drawing
        canvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR)
        // Move the canvas coordinates to the initial position
        canvas.translate(0F, 0F)
        // Draw background
        canvas.drawRect(
            0F,
            0F,
            width.toFloat(),
            height.toFloat(),
            paint
        )
        // Let's draw keyboard keys
        if (invalidatedKey != null) {
            // If there is an invalidated key, draw it alone, not all the keys.
            onKeyDraw(invalidatedKey)
        } else {
            // Draw all the keys
            for (key in keyboard.keys) {
                onKeyDraw(key)
            }
        }
        // Overlay a dark rectangle to dim the keyboard
        paint.color = scrimColor
        paint.alpha = scrimAlpha
        canvas.drawRect(0F, 0F, width.toFloat(), height.toFloat(), paint)
        // Reset states
        drawPending = false
        dirtyRect.setEmpty()
    }

    private fun onKeyDraw(key: Keyboard.Key) {
        val canvas = this.canvas ?: return
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
        // Save the canvas coordinate states before drawing
        canvas.save()
        // Translate the canvas coordinates to the key x, y position
        canvas.translate((key.x + paddingLeft).toFloat(), (key.y + paddingTop).toFloat())
        // Draw the keyBackground
        keyBackground?.draw(canvas)
        // Switch the character to uppercase if shift is pressed
        val keyLabel = key.adjustCase(getLocale()).toString()
        if (keyLabel.isNotEmpty()) {
            // Use primary color for letters and digits, secondary color for everything else
            paint.color = when {
                Character.isLetterOrDigit(keyLabel[0]) -> {
                    keyTextColorPrimary
                }
                useKeyTextColorSecondary -> {
                    keyTextColorSecondary
                }
                else -> {
                    keyTextColorPrimary
                }
            }
            // For characters, use large font. For labels like "Done", use small font.
            if (keyLabel.length > 1 && key.codes.size < 2) {
                paint.textSize = labelTextSize.toFloat()
            } else if (keyLabel.isPunctuation()) {
                paint.textSize = keyPunctuationSize.toFloat()
            } else {
                paint.textSize = keyTextSize.toFloat()
            }
            // Draw the text
            canvas.drawText(
                keyLabel,
                (key.width - paddingLeft - paddingRight) / 2F + paddingLeft,
                (key.height - paddingTop - paddingBottom) / 2F +
                    (paint.textSize - paint.descent()) / 2F + paddingTop,
                paint
            )
            // Turn off drop shadow
            paint.setShadowLayer(0f, 0f, 0f, 0)
        } else if (key.icon != null) {
            val x = (
                key.width - paddingLeft - paddingRight - key.icon.intrinsicWidth
                ) / 2F + paddingLeft
            val y = (
                key.height - paddingTop - paddingBottom - key.icon.intrinsicHeight
                ) / 2F + paddingTop
            canvas.translate(x, y)
            // Draw the key icon
            key.icon.draw(canvas)
        }
        // Restore the canvas coordinate states after drawing
        canvas.restore()
    }

    /**
     * Requests a redraw of the entire keyboard. Calling [invalidate] is not sufficient
     * because the keyboard renders the keys to an off-screen buffer and an [invalidate] only
     * draws the cached buffer.
     * @see [invalidateKey]
     */
    private fun invalidateAllKeys() {
        dirtyRect.union(0, 0, width, height)
        drawPending = true
        postInvalidate()
    }

    /**
     * Invalidates a key so that it will be redrawn on the next repaint. Use this method if only
     * one key is changing it's content. Any changes that affect the position or size of the key
     * may not be honored.
     * @param keyIndex the index of the key in the attached [Keyboard].
     * @see [invalidateAllKeys]
     */
    private fun invalidateKey(keyIndex: Int) {
        val keyboard = this.keyboard ?: return
        val keys = keyboard.keys
        if (keyIndex < 0 || keyIndex >= keys.size) {
            return
        }
        val key = keys[keyIndex]
        // Restricting (clipping) drawing area to the single invalidated key
        val left = key.x + paddingLeft
        val top = key.y + paddingTop
        val right = key.x + key.width + paddingLeft
        val bottom = key.y + key.height + paddingTop
        dirtyRect.union(left, top, right, bottom)
        // Redraw the canvas for the single invalidated key
        onBufferDraw(key)
        // Invalidate to draw the buffer again
        postInvalidate(left, top, right, bottom)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // TODO: 06-07-2021 WIP fix touched key is not highlighting in UI
        // Convert multi-pointer up/down events to single up/down events to
        // deal with the typical multi-pointer behavior of two-thumb typing
        val result = onModifiedTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        return result
    }

    private fun onModifiedTouchEvent(event: MotionEvent): Boolean {
        // TODO: 06-07-2021 WIP fix touched key is not highlighting in UI
        val keyboard = this.keyboard ?: return true
        val touchX = (event.x - paddingLeft).toInt()
        val touchY = (event.y - paddingTop).toInt()
        val keyIndex = keyboard.getKeyIndex(touchX, touchY)
        if (keyIndex == Keyboard.NOT_A_KEY) return true
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                keyboard.keys[keyIndex].onPressed()
                invalidateKey(keyIndex)
            }
            MotionEvent.ACTION_UP -> {
                keyboard.keys[keyIndex].onReleased(true)
                invalidateKey(keyIndex)
            }
            MotionEvent.ACTION_CANCEL -> {
                invalidateKey(keyIndex)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        ImeApp.printLog(TAG, "performClick")
        return super.performClick()
    }
}
