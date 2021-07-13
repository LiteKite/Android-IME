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
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.litekite.ime.R
import com.litekite.ime.app.ImeApp
import com.litekite.ime.base.CallbackProvider
import com.litekite.ime.util.ContextUtil.themeContext
import com.litekite.ime.util.StringUtil.isPunctuation
import java.util.Locale
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
) : View(context, attrs, defStyleAttr), CallbackProvider<KeyboardView.KeyboardActionListener> {

    companion object {
        private val TAG: String = KeyboardView::class.java.simpleName

        private const val SCRIM_ALPHA = 242 // 95% opacity.
        private const val MAX_ALPHA = 255

        private const val REPEAT_KEY_DELAY = 50L // ~20 keys per second
        private const val REPEAT_KEY_START_DELAY = 400L
    }

    private var scrimColor: Int
    private var scrimAlpha = 0
    private val keyBackground: Drawable?
    private val keyBgPadding = Rect(0, 0, 0, 0)
    private val fontFamily: String?
    private var textStyle = Typeface.NORMAL
    private var keyTextSize = 18
    private var labelTextSize = 14
    private val keyPunctuationSize: Int
    private var keyTextColorPrimary = -0x1000000
    private val useKeyTextColorSecondary: Boolean
    private var keyTextColorSecondary = -0x67000000

    internal var keyboard: Keyboard? = null

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

    /** Touch properties  */
    private var currentKeyIndex = Keyboard.NOT_A_KEY
    private var abortKey = false

    /** Variables for dealing with multiple pointers */
    private var lastPointerCount = 1
    private var lastPointerX = 0f
    private var lastPointerY = 0f

    private val performLongPress = Runnable {
        if (isPressed && isLongClickable) {
            performLongClick()
        }
    }

    private val performRepeatKey = object : Runnable {
        override fun run() {
            sendKeyEvent()
            postDelayed(this, REPEAT_KEY_DELAY)
        }
    }

    override val callbacks: ArrayList<KeyboardActionListener> = ArrayList()

    /** The accessibility manager for accessibility support  */
    private var accessibilityManager: AccessibilityManager? = null

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
        keyBackground?.getPadding(keyBgPadding)
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
        accessibilityManager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }

    /**
     * Attaches a keyboard to this view. The keyboard can be switched at any time and the
     * view will re-layout itself to accommodate the keyboard.
     * @see Keyboard
     * @param keyboard the keyboard to display in this view
     */
    fun setKeyboard(keyboard: Keyboard) {
        removeCallbacks()
        this.keyboard = keyboard
        abortKey = true // Perform touch only until the next ACTION_DOWN
        keyboardChanged = true
        currentKeyIndex = Keyboard.NOT_A_KEY
        invalidateAllKeys()
    }

    /**
     * Sets the state of the shift key of the keyboard, if any.
     * @param shifted whether or not to enable the state of the shift key
     * @return true if the shift key state changed, false if there was no change
     * @see isShifted
     */
    fun setShifted(shifted: Boolean): Boolean {
        val keyboard = this.keyboard ?: return false
        if (keyboard.setShifted(shifted)) {
            // The whole keyboard probably needs to be redrawn
            invalidateAllKeys()
            return true
        }
        return false
    }

    /**
     * Returns the state of the shift key of the keyboard, if any.
     * @return true if the shift is in a pressed state, false otherwise. If there is
     * no shift key on the keyboard or there is no keyboard attached, it returns false.
     * @see KeyboardView.setShifted
     */
    fun isShifted(): Boolean {
        return keyboard?.isShifted ?: return false
    }

    fun getLocale(): Locale = resources.configuration.locales[0]

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
        }
        val keyboard = this.keyboard ?: return
        val canvas = this.canvas ?: return
        // Set the Bitmap
        canvas.setBitmap(buffer)
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
                (key.width - keyBgPadding.left - keyBgPadding.right) / 2F + keyBgPadding.left,
                (key.height - keyBgPadding.top - keyBgPadding.bottom) / 2F +
                    (paint.textSize - paint.descent()) / 2F + keyBgPadding.top,
                paint
            )
            // Turn off drop shadow
            paint.setShadowLayer(0f, 0f, 0f, 0)
        } else if (key.icon != null) {
            val x = (
                key.width - keyBgPadding.left - keyBgPadding.right - key.icon.intrinsicWidth
                ) / 2F + keyBgPadding.left
            val y = (
                key.height - keyBgPadding.top - keyBgPadding.bottom - key.icon.intrinsicHeight
                ) / 2F + keyBgPadding.top
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

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // Close the touch keyboard when the user scrolls.
        if (event.actionMasked == MotionEvent.ACTION_SCROLL) {
            callbacks.forEach { it.onStopInput() }
            return true
        }
        return false
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (accessibilityManager?.isTouchExplorationEnabled == true && event.pointerCount == 1) {
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    event.action = MotionEvent.ACTION_DOWN
                }
                MotionEvent.ACTION_HOVER_MOVE -> {
                    event.action = MotionEvent.ACTION_MOVE
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    event.action = MotionEvent.ACTION_UP
                }
            }
            return onTouchEvent(event)
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // TODO: 08-07-2021 WIP fix vertical gap, space bar key, edgeFlags, change key icons
        // TODO: 08-07-2021 WIP Themes and Styles for Day/Night Mode
        // TODO: 12-07-2021 Add Key Preview popup window and popup characters window
        // TODO: 12-07-2021 Screen size support for all DPIs and flexible key sizes
        // TODO: 13-07-2021 Draw touch points in debug mode and fix shift keys
        // Convert multi-pointer up/down events to single up/down events to
        // deal with the typical multi-pointer behavior of two-thumb typing
        val pointerCount = event.pointerCount
        var result: Boolean
        if (pointerCount == lastPointerCount) {
            // Single Pointer
            if (pointerCount == 1) {
                result = handleTouchEvent(event)
                lastPointerX = event.x
                lastPointerY = event.y
            } else {
                // Don't do anything when multi pointers are down and moving.
                result = true
            }
        } else {
            // Single pointer
            if (pointerCount == 1) {
                // Send a down event for the latest pointer
                val down = MotionEvent.obtain(
                    event.eventTime,
                    event.eventTime,
                    MotionEvent.ACTION_DOWN,
                    event.x,
                    event.y,
                    event.metaState
                )
                result = handleTouchEvent(down)
                down.recycle()
                // If it's an up action, then deliver the up as well.
                if (event.action == MotionEvent.ACTION_UP) {
                    result = handleTouchEvent(event)
                }
            } else {
                // Multi pointers
                // Send an up event for the last pointer
                val up = MotionEvent.obtain(
                    event.eventTime,
                    event.eventTime,
                    MotionEvent.ACTION_UP,
                    lastPointerX,
                    lastPointerY,
                    event.metaState
                )
                result = handleTouchEvent(up)
                up.recycle()
            }
        }
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        lastPointerCount = pointerCount
        return result
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        val keyboard = this.keyboard ?: return true
        // Ignore all motion events until a DOWN.
        if (abortKey &&
            event.action != MotionEvent.ACTION_DOWN &&
            event.action != MotionEvent.ACTION_CANCEL
        ) {
            return true
        }
        val keys = keyboard.keys
        val touchX = (event.x - paddingLeft).toInt()
        val touchY = (event.y - paddingTop).toInt()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                abortKey = false
                val keyIndex = keyboard.getKeyIndex(touchX, touchY)
                if (keyIndex == Keyboard.NOT_A_KEY) {
                    return true
                }
                val currentKey = keys[keyIndex]
                currentKey.onPressed()
                currentKeyIndex = keyIndex
                invalidateKey(currentKeyIndex)
                postDelayed(performLongPress, ViewConfiguration.getLongPressTimeout().toLong())
                if (currentKey.isRepeatable) {
                    sendKeyEvent()
                    postDelayed(performRepeatKey, REPEAT_KEY_START_DELAY)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                removeCallbacks()
                if (currentKeyIndex == Keyboard.NOT_A_KEY) {
                    return true
                }
                val currentKey = keys[currentKeyIndex]
                if (currentKey.isPressed && !currentKey.isInside(touchX, touchY)) {
                    currentKey.onReleased(false)
                    invalidateKey(currentKeyIndex)
                }
                postDelayed(performLongPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                abortKey = true
                removeCallbacks()
                if (currentKeyIndex == Keyboard.NOT_A_KEY) {
                    return true
                }
                val currentKey = keys[currentKeyIndex]
                if (currentKey.isPressed) {
                    val isInside = currentKey.isInside(touchX, touchY)
                    currentKey.onReleased(isInside)
                    invalidateKey(currentKeyIndex)
                }
                // If we're not on a repeating key (which sends on a DOWN event)
                if (!currentKey.isRepeatable) {
                    sendKeyEvent()
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        ImeApp.printLog(TAG, "performClick")
        return super.performClick()
    }

    private fun sendKeyEvent() {
        if (currentKeyIndex == Keyboard.NOT_A_KEY) {
            return
        }
        val keyboard = this.keyboard ?: return
        val key = keyboard.keys[currentKeyIndex]
        callbacks.forEach { it.onKey(key.codes[0]) }
    }

    private fun removeCallbacks() {
        removeCallbacks(performLongPress)
        removeCallbacks(performRepeatKey)
    }

    private fun close() {
        removeCallbacks()
        buffer = null
        canvas = null
    }

    override fun onDetachedFromWindow() {
        close()
        super.onDetachedFromWindow()
    }

    /**
     * Listener for virtual keyboard events.
     */
    interface KeyboardActionListener {

        /**
         * Send a key press to the listener.
         * @param primaryCode this is the key that was pressed
         */
        fun onKey(primaryCode: Int)

        /**
         * Called when we want to stop keyboard input.
         */
        fun onStopInput()
    }
}
