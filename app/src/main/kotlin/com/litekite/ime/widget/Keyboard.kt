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
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.util.Xml
import com.litekite.ime.R
import com.litekite.ime.util.DimensUtil.getDimensionOrFraction
import com.litekite.ime.util.StringUtil.parseCSV
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.util.Locale

/**
 * Loads an XML description of a keyboard and stores the attributes of the keys.
 *
 * A keyboard consists of rows of keys.
 *
 * <p>The layout file for a keyboard contains XML that looks like the following snippet:</p>
 *
 * <pre>
 * &lt;keyboard
 *         app:keyWidth="%10p"
 *         app:keyHeight="50px"
 *         app:keyHorizontalGap="2px"
 *         app:keyVerticalGap="2px" &gt;
 *     &lt;Row app:keyWidth="32px" &gt;
 *         &lt;Key app:keyLabel="A" /&gt;
 *         ...
 *     &lt;/Row&gt;
 *     ...
 * &lt;/keyboard&gt;
 * </pre>
 *
 * @attr ref R.styleable#Keyboard_keyWidth
 * @attr ref R.styleable#Keyboard_keyHeight
 * @attr ref R.styleable#Keyboard_keyHorizontalGap
 * @attr ref R.styleable#Keyboard_keyVerticalGap
 *
 * @author Vignesh S
 * @version 1.0, 14/06/2021
 * @since 1.0
 */
class Keyboard(context: Context, layoutRes: Int) {

    companion object {

        /** Keyboard types */
        const val DEF_TYPE = "xml"
        const val LAYOUT_KEYBOARD_QWERTY = "keyboard_qwerty"
        const val LAYOUT_KEYBOARD_SYMBOL = "keyboard_symbol"

        /** Xml layout tags */
        const val TAG_KEYBOARD = "Keyboard"
        const val TAG_ROW = "Row"
        const val TAG_KEY = "Key"

        /** Edge flags */
        const val EDGE_LEFT = 0x01
        const val EDGE_RIGHT = 0x02
        const val EDGE_TOP = 0x04
        const val EDGE_BOTTOM = 0x08

        /** Modifier keys */
        const val KEYCODE_SHIFT = -1
        const val KEYCODE_MODE_CHANGE = -2
        const val KEYCODE_CANCEL = -3
        const val KEYCODE_DONE = -4
        const val KEYCODE_DELETE = -5
        const val KEYCODE_ALT = -6
        const val KEYCODE_CLOSE_KEYBOARD = -99

        const val NOT_A_KEY = -1

        /** Keyboard key drawable states */
        private val KEY_STATE_NORMAL = intArrayOf()

        private val KEY_STATE_PRESSED = intArrayOf(
            android.R.attr.state_pressed
        )

        private val KEY_STATE_NORMAL_ON = intArrayOf(
            android.R.attr.state_checkable,
            android.R.attr.state_checked
        )

        private val KEY_STATE_NORMAL_OFF = intArrayOf(
            android.R.attr.state_checkable
        )

        private val KEY_STATE_PRESSED_ON = intArrayOf(
            android.R.attr.state_pressed,
            android.R.attr.state_checkable,
            android.R.attr.state_checked
        )

        private val KEY_STATE_PRESSED_OFF = intArrayOf(
            android.R.attr.state_pressed,
            android.R.attr.state_checkable
        )
    }

    /** Width of the screen available to fit the Keyboard */
    private val displayWidth: Int = context.resources.displayMetrics.widthPixels

    /** Height of the screen */
    private val displayHeight: Int = context.resources.displayMetrics.heightPixels

    /**
     * Total width of the Keyboard, including left side gaps and keys, but not any gaps on the
     * right side.
     */
    internal var keyboardWidth: Int = 0

    /**
     * Total height of the Keyboard, including the padding and keys
     */
    internal var keyboardHeight: Int = 0

    /** key default width */
    private var defaultKeyWidth: Int = displayWidth / 10

    /** key default height */
    private var defaultKeyHeight: Int = defaultKeyWidth

    /** Key horizontal default gap for all rows */
    private var defaultKeyHorizontalGap = 0

    /** Key vertical default gap between rows */
    private var defaultKeyVerticalGap = 0

    /** List of rows in this Keyboard */
    private val rows: ArrayList<Row> = ArrayList()

    /** List of keys in this Keyboard */
    internal val keys: ArrayList<Key> = ArrayList()

    /**
     * Is the Keyboard in the shifted state
     */
    internal var isShifted = false

    /**
     * Key instance for the shift key, if present
     */
    private val shiftKeys = arrayOf<Keyboard.Key?>(null, null)

    /** List of modifier keys such as Shift & Alt, if any */
    private val modifierKeys: ArrayList<Key> = ArrayList()

    /**
     * Keyboard mode, or zero, if none.
     */
    private val keyboardMode: Int = 0

    init {
        // Parses Keyboard attributes
        loadKeyboard(context, context.resources.getXml(layoutRes))
    }

    @Throws(XmlPullParserException::class)
    private fun loadKeyboard(context: Context, parser: XmlResourceParser) {
        var x = 0
        var y = 0
        var inKey = false
        var inRow = false
        var currentRow: Row? = null
        var currentKey: Key? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    TAG_KEYBOARD -> {
                        parseKeyboardAttributes(context.resources, parser)
                    }
                    TAG_ROW -> {
                        x = 0
                        inRow = true
                        currentRow = Row(context.resources, parser)
                        rows.add(currentRow)
                        val skipRow = currentRow.keyboardMode != 0 &&
                            currentRow.keyboardMode != keyboardMode
                        if (skipRow) {
                            skipToEndOfRow(parser)
                            inRow = false
                        }
                    }
                    TAG_KEY -> {
                        inKey = true
                        if (currentRow != null) {
                            currentKey = Key(context.resources, x, y, parser, currentRow)
                            keys.add(currentKey)
                            if (currentKey.codes.isNotEmpty() &&
                                currentKey.codes[0] == KEYCODE_SHIFT
                            ) {
                                // Find available shift key slot and put this shift key in it
                                for (i in shiftKeys.indices) {
                                    if (shiftKeys[i] == null) {
                                        shiftKeys[i] = currentKey
                                        break
                                    }
                                }
                                modifierKeys.add(currentKey)
                            } else if (currentKey.codes.isNotEmpty() &&
                                currentKey.codes[0] == KEYCODE_ALT
                            ) {
                                modifierKeys.add(currentKey)
                            }
                            currentRow.keys.add(currentKey)
                        }
                    }
                }
            } else if (parser.eventType == XmlPullParser.END_TAG) {
                if (inKey) {
                    inKey = false
                    if (currentKey != null) {
                        x += currentKey.width + currentKey.horizontalGap
                        if (x > keyboardWidth) {
                            keyboardWidth = x
                        }
                    }
                } else if (inRow) {
                    inRow = false
                    if (currentRow != null) {
                        y += currentRow.keyHeight + currentRow.keyVerticalGap
                    }
                }
            }
        }
        keyboardHeight = y - defaultKeyVerticalGap
    }

    private fun parseKeyboardAttributes(res: Resources, parser: XmlResourceParser) {
        parser.require(XmlPullParser.START_TAG, null, TAG_KEYBOARD)
        val ta = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard)
        defaultKeyWidth = ta.getDimensionOrFraction(
            R.styleable.Keyboard_keyWidth,
            displayWidth,
            defaultKeyWidth
        )
        defaultKeyHeight = ta.getDimensionOrFraction(
            R.styleable.Keyboard_keyHeight,
            displayHeight,
            defaultKeyHeight
        )
        defaultKeyHorizontalGap = ta.getDimensionOrFraction(
            R.styleable.Keyboard_keyHorizontalGap,
            displayWidth,
            defaultKeyHorizontalGap
        )
        defaultKeyVerticalGap = ta.getDimensionOrFraction(
            R.styleable.Keyboard_keyVerticalGap,
            displayHeight,
            defaultKeyVerticalGap
        )
        ta.recycle()
    }

    /**
     * Returns the index of the key that the touch point falls into it.
     *
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     *
     * @return the index of the key.
     */
    fun getKeyIndex(x: Int, y: Int): Int {
        for (index in keys.indices) {
            if (keys[index].isInside(x, y)) return index
        }
        return NOT_A_KEY
    }

    fun setShifted(shiftState: Boolean): Boolean {
        for (shiftKey in shiftKeys) {
            if (shiftKey != null) {
                shiftKey.isOn = shiftState
            }
        }
        if (isShifted != shiftState) {
            isShifted = shiftState
            return true
        }
        return false
    }

    /**
     * Container for keys in the Keyboard. All keys in a row are at the same Y-coordinate.
     * Some of the key size defaults can be overridden per row from what the {@link Keyboard}
     * defines.
     *
     * @attr ref R.styleable#Keyboard_keyWidth
     * @attr ref R.styleable#Keyboard_keyHeight
     * @attr ref R.styleable#Keyboard_keyHorizontalGap
     * @attr ref R.styleable#Keyboard_keyVerticalGap
     * @attr ref R.styleable#Keyboard_Row_rowEdgeFlags
     * @attr ref R.styleable#Keyboard_Row_keyboardMode
     */
    inner class Row(res: Resources, parser: XmlResourceParser) {

        /** Width of a key in this row. */
        private val keyWidth: Int

        /** Height of a key in this row. */
        internal val keyHeight: Int

        /** Key horizontal gap between keys in this row. */
        private val keyHorizontalGap: Int

        /** Key vertical gap following this row. */
        internal val keyVerticalGap: Int

        /**
         * Edge flags for this row of keys.
         * Possible values that can be assigned are {@link Keyboard#ROW_EDGE_TOP ROW_EDGE_LEFT}
         * and {@link Keyboard#ROW_EDGE_BOTTOM ROW_EDGE_BOTTOM}
         */
        internal val rowEdgeFlags: Int

        /** The Keyboard mode for this row  */
        internal val keyboardMode: Int

        internal val keys: ArrayList<Key> = ArrayList()

        init {
            parser.require(XmlPullParser.START_TAG, null, TAG_ROW)
            var ta = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard)
            keyWidth = ta.getDimensionOrFraction(
                R.styleable.Keyboard_keyWidth,
                displayWidth,
                defaultKeyWidth
            )
            keyHeight = ta.getDimensionOrFraction(
                R.styleable.Keyboard_keyHeight,
                displayHeight,
                defaultKeyHeight
            )
            keyHorizontalGap = ta.getDimensionOrFraction(
                R.styleable.Keyboard_keyHorizontalGap,
                displayWidth,
                defaultKeyHorizontalGap
            )
            keyVerticalGap = ta.getDimensionOrFraction(
                R.styleable.Keyboard_keyVerticalGap,
                displayHeight,
                defaultKeyVerticalGap
            )
            ta.recycle()
            ta = res.obtainAttributes(
                Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Row
            )
            rowEdgeFlags = ta.getInt(
                R.styleable.Keyboard_Row_rowEdgeFlags,
                0
            )
            keyboardMode = ta.getResourceId(
                R.styleable.Keyboard_Row_keyboardMode,
                0
            )
            ta.recycle()
        }
    }

    private fun skipToEndOfRow(parser: XmlResourceParser) {
        while (parser.next() != XmlResourceParser.END_DOCUMENT) {
            if (parser.eventType == XmlResourceParser.END_TAG && parser.name == TAG_ROW) {
                break
            }
        }
    }

    /**
     * Class for describing the position and characteristics of a single key in the Keyboard.
     *
     * @attr ref R.styleable#Keyboard_keyWidth
     * @attr ref R.styleable#Keyboard_keyHeight
     * @attr ref R.styleable#Keyboard_keyHorizontalGap
     * @attr ref R.styleable#Keyboard_Key_codes
     * @attr ref R.styleable#Keyboard_Key_keyIcon
     * @attr ref R.styleable#Keyboard_Key_keyLabel
     * @attr ref R.styleable#Keyboard_Key_iconPreview
     * @attr ref R.styleable#Keyboard_Key_isSticky
     * @attr ref R.styleable#Keyboard_Key_isRepeatable
     * @attr ref R.styleable#Keyboard_Key_isModifier
     * @attr ref R.styleable#Keyboard_Key_popupKeyboard
     * @attr ref R.styleable#Keyboard_Key_popupCharacters
     * @attr ref R.styleable#Keyboard_Key_keyOutputText
     * @attr ref R.styleable#Keyboard_Key_keyEdgeFlags
     */
    inner class Key(
        res: Resources,
        x: Int,
        y: Int,
        parser: XmlResourceParser,
        parentRow: Row
    ) {

        /** X coordinate of the key in the Keyboard layout  */
        val x: Int

        /** Y coordinate of the key in the Keyboard layout  */
        val y: Int

        /** Width of the key, not including the gap */
        internal val width: Int

        /** Height of the key, not including the gap */
        internal val height: Int

        /** Key horizontal gap before this key. */
        internal val horizontalGap: Int

        /**
         * All the key codes (unicode or custom code) that this key could generate,
         * zeroth being the most important.
         */
        internal var codes = intArrayOf()

        /**
         * If this key pops up a mini Keyboard,
         * this is the resource id for the XML layout for that Keyboard.
         */
        private val popupKeyboardResId: Int

        /** Popup characters  */
        private var popupKeyboardChars: CharSequence = ""

        /**
         * Flags that specify the anchoring to edges of the Keyboard for detecting touch events
         * that are just out of the boundary of the key.
         *
         * Possible values that can be assigned are {@link Keyboard#KEY_EDGE_TOP KEY_EDGE_LEFT}
         * and {@link Keyboard#KEY_EDGE_BOTTOM KEY_EDGE_BOTTOM}
         */
        private val edgeFlags: Int

        /** Whether this is a modifier key, such as Shift or Alt  */
        private val modifier: Boolean

        /** Whether this key is sticky, i.e., a toggle key  */
        private val sticky: Boolean

        /** If this is a sticky key, is it on?  */
        internal var isOn = false

        /** Whether this key repeats itself when held down  */
        internal val isRepeatable: Boolean

        /** Preview version of the icon, for the preview popup  */
        private val iconPreview: Drawable?

        /** Text to output when pressed. This can be multiple characters, like ".com"  */
        private var outputText: CharSequence = ""

        /** Label to display  */
        private var label: CharSequence = ""

        /** Icon to display instead of a label. Icon takes precedence over a label  */
        internal val icon: Drawable?

        /**
         * The current pressed state of this key
         */
        private var _isPressed = false
        internal val isPressed get() = _isPressed

        init {
            parser.require(XmlPullParser.START_TAG, null, TAG_KEY)
            var ta = res.obtainAttributes(Xml.asAttributeSet(parser), R.styleable.Keyboard)
            width = ta.getDimensionOrFraction(
                R.styleable.Keyboard_keyWidth,
                displayWidth,
                defaultKeyWidth
            )
            height = ta.getDimensionOrFraction(
                R.styleable.Keyboard_keyHeight,
                displayHeight,
                defaultKeyHeight
            )
            horizontalGap = ta.getDimensionOrFraction(
                R.styleable.Keyboard_keyHorizontalGap,
                displayWidth,
                defaultKeyHorizontalGap
            )
            this.x = x + horizontalGap
            this.y = y
            ta.recycle()
            ta = res.obtainAttributes(
                Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Key
            )
            val keyCodesTypedVal = TypedValue()
            ta.getValue(
                R.styleable.Keyboard_Key_codes,
                keyCodesTypedVal
            )
            if (keyCodesTypedVal.type == TypedValue.TYPE_INT_DEC ||
                keyCodesTypedVal.type == TypedValue.TYPE_INT_HEX
            ) {
                codes = intArrayOf(keyCodesTypedVal.data)
            } else if (keyCodesTypedVal.type == TypedValue.TYPE_STRING) {
                codes = keyCodesTypedVal.string.toString().parseCSV()
            }
            popupKeyboardResId = ta.getResourceId(
                R.styleable.Keyboard_Key_popupKeyboard,
                0
            )
            popupKeyboardChars = ta.getText(R.styleable.Keyboard_Key_popupCharacters) ?: ""
            edgeFlags = ta.getInt(
                R.styleable.Keyboard_Key_keyEdgeFlags,
                0
            ) or parentRow.rowEdgeFlags
            modifier = ta.getBoolean(
                R.styleable.Keyboard_Key_isModifier,
                false
            )
            sticky = ta.getBoolean(
                R.styleable.Keyboard_Key_isSticky,
                false
            )
            isRepeatable = ta.getBoolean(R.styleable.Keyboard_Key_isRepeatable, false)
            iconPreview = ta.getDrawable(R.styleable.Keyboard_Key_iconPreview)
            iconPreview?.apply {
                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            }
            outputText = ta.getText(R.styleable.Keyboard_Key_keyOutputText) ?: ""
            label = ta.getText(R.styleable.Keyboard_Key_keyLabel) ?: ""
            icon = ta.getDrawable(R.styleable.Keyboard_Key_keyIcon)
            icon?.apply {
                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            }
            if (codes.isEmpty() && label.isNotEmpty()) {
                codes = intArrayOf(label[0].code)
            }
            ta.recycle()
        }

        /**
         * Detects if a point falls inside this key.
         *
         * @param x the x-coordinate of the point
         * @param y the y-coordinate of the point
         *
         * @return whether or not the point falls inside the key. If the key is attached to an edge,
         * it will assume that all points between the key and the edge are considered to be inside
         * the key.
         */
        fun isInside(x: Int, y: Int): Boolean {
            val leftEdge = edgeFlags and EDGE_LEFT > 0
            val rightEdge = edgeFlags and EDGE_RIGHT > 0
            val topEdge = edgeFlags and EDGE_TOP > 0
            val bottomEdge = edgeFlags and EDGE_BOTTOM > 0
            return (
                (x >= this.x || leftEdge && x <= this.x + width) &&
                    (x < this.x + width || rightEdge && x >= this.x) &&
                    (y >= this.y || topEdge && y <= this.y + height) &&
                    (y < this.y + height || bottomEdge && y >= this.y)
                )
        }

        fun adjustCase(locale: Locale): CharSequence {
            var label = this.label
            if (isShifted &&
                label.isNotEmpty() &&
                label.length < 3 &&
                Character.isLowerCase(label[0])
            ) {
                label = label.toString().uppercase(locale)
            }
            return label
        }

        /**
         * Informs the key that it has been pressed, in case it needs to change its appearance or
         * state.
         * @see [onReleased]
         */
        fun onPressed() {
            _isPressed = true
        }

        /**
         * Changes the pressed state of the key.
         *
         * <p>Toggled state of the key will be flipped when all the following conditions are
         * fulfilled:</p>
         *
         * <ul>
         *     <li>This is a sticky key, that is, {@link #sticky} is {@code true}.
         *     <li>The parameter {@code inside} is {@code true}.
         *     <li>{@link android.os.Build.VERSION#SDK_INT} is greater than
         *         {@link android.os.Build.VERSION_CODES#LOLLIPOP_MR1}.
         * </ul>
         *
         * @param inside whether the finger was released inside the key. Works only on Android M and
         * later. See the method document for details.
         * @see #onPressed()
         */
        fun onReleased(inside: Boolean) {
            _isPressed = false
            if (sticky && inside) {
                isOn = !isOn
            }
        }

        /**
         * Returns the drawable state for the key, based on the current state and type of the key.
         *
         * @return the drawable state of the key.
         * @see android.graphics.drawable.StateListDrawable.setState
         */
        fun getDrawableState(): IntArray {
            var states: IntArray = KEY_STATE_NORMAL
            if (isOn) {
                states = if (_isPressed) {
                    KEY_STATE_PRESSED_ON
                } else {
                    KEY_STATE_NORMAL_ON
                }
            } else {
                if (sticky) {
                    states = if (_isPressed) {
                        KEY_STATE_PRESSED_OFF
                    } else {
                        KEY_STATE_NORMAL_OFF
                    }
                } else {
                    if (_isPressed) {
                        states = KEY_STATE_PRESSED
                    }
                }
            }
            return states
        }
    }
}
