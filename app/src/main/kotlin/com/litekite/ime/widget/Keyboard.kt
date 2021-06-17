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

/**
 * Loads an XML description of a keyboard and stores the attributes of the keys.
 *
 * A keyboard consists of rows of keys.
 *
 * <p>The layout file for a keyboard contains XML that looks like the following snippet:</p>
 *
 * <pre>
 * &lt;keyboard
 *         android:keyWidth="%10p"
 *         android:keyHeight="50px"
 *         android:keyHorizontalGap="2px"
 *         android:keyVerticalGap="2px" &gt;
 *     &lt;Row android:keyWidth="32px" &gt;
 *         &lt;Key android:keyLabel="A" /&gt;
 *         ...
 *     &lt;/Row&gt;
 *     ...
 * &lt;/keyboard&gt;
 * </pre>
 *
 * @attr ref android.R.styleable#Keyboard_keyWidth
 * @attr ref android.R.styleable#Keyboard_keyHeight
 * @attr ref android.R.styleable#Keyboard_keyHorizontalGap
 * @attr ref android.R.styleable#Keyboard_keyVerticalGap
 *
 * @author Vignesh S
 * @version 1.0, 14/06/2021
 * @since 1.0
 */
class Keyboard(context: Context, layoutRes: Int) {

    companion object {
        /** Xml layout tags */
        const val TAG_KEYBOARD = "keyboard"
        const val TAG_ROW = "Row"
        const val TAG_KEY = "Key"

        /** Row edge flags */
        const val ROW_EDGE_LEFT = 0x01
        const val ROW_EDGE_RIGHT = 0x02
        const val ROW_EDGE_TOP = 0x04
        const val ROW_EDGE_BOTTOM = 0x08
    }

    /** Width of the screen available to fit the Keyboard */
    private val displayWidth: Int = context.resources.displayMetrics.widthPixels

    /** Height of the screen */
    private val displayHeight: Int = context.resources.displayMetrics.heightPixels

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
    private val keys: ArrayList<Key> = ArrayList()

    init {
        // Parses Keyboard attributes
        loadKeyboard(context, context.resources.getXml(layoutRes))
    }

    private fun loadKeyboard(context: Context, parser: XmlResourceParser) {
        var currentRow: Row? = null
        parser.require(XmlPullParser.START_DOCUMENT, null, TAG_KEYBOARD)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    TAG_KEYBOARD -> {
                        parseKeyboardAttributes(context.resources, parser)
                    }
                    TAG_ROW -> {
                        currentRow = Row(context.resources, parser)
                        rows.add(currentRow)
                    }
                    TAG_KEY -> {
                        keys.add(Key(context.resources, parser, currentRow!!))
                    }
                }
            }
        }
    }

    private fun parseKeyboardAttributes(res: Resources, parser: XmlResourceParser) {
        parser.require(XmlPullParser.START_DOCUMENT, null, TAG_KEYBOARD)
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
     * Container for keys in the Keyboard. All keys in a row are at the same Y-coordinate.
     * Some of the key size defaults can be overridden per row from what the {@link Keyboard}
     * defines.
     *
     * @attr ref android.R.styleable#Keyboard_keyWidth
     * @attr ref android.R.styleable#Keyboard_keyHeight
     * @attr ref android.R.styleable#Keyboard_keyHorizontalGap
     * @attr ref android.R.styleable#Keyboard_keyVerticalGap
     * @attr ref android.R.styleable#Keyboard_Row_rowEdgeFlags
     * @attr ref android.R.styleable#Keyboard_Row_keyboardMode
     */
    inner class Row(res: Resources, parser: XmlResourceParser) {

        /** Width of a key in this row. */
        private val keyWidth: Int

        /** Height of a key in this row. */
        private val keyHeight: Int

        /** Key horizontal gap between keys in this row. */
        private val keyHorizontalGap: Int

        /** Key vertical gap following this row. */
        private val keyVerticalGap: Int

        /**
         * Edge flags for this row of keys.
         * Possible values that can be assigned are {@link Keyboard#ROW_EDGE_TOP ROW_EDGE_LEFT}
         * and {@link Keyboard#ROW_EDGE_BOTTOM ROW_EDGE_BOTTOM}
         */
        internal val rowEdgeFlags: Int

        /** The Keyboard mode for this row  */
        private val keyboardMode: Int

        private val keys: ArrayList<Key> = ArrayList()

        init {
            parser.require(XmlPullParser.START_DOCUMENT, null, TAG_ROW)
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

    /**
     * Class for describing the position and characteristics of a single key in the Keyboard.
     *
     * @attr ref android.R.styleable#Keyboard_keyWidth
     * @attr ref android.R.styleable#Keyboard_keyHeight
     * @attr ref android.R.styleable#Keyboard_keyHorizontalGap
     * @attr ref android.R.styleable#Keyboard_Key_codes
     * @attr ref android.R.styleable#Keyboard_Key_keyIcon
     * @attr ref android.R.styleable#Keyboard_Key_keyLabel
     * @attr ref android.R.styleable#Keyboard_Key_iconPreview
     * @attr ref android.R.styleable#Keyboard_Key_isSticky
     * @attr ref android.R.styleable#Keyboard_Key_isRepeatable
     * @attr ref android.R.styleable#Keyboard_Key_isModifier
     * @attr ref android.R.styleable#Keyboard_Key_popupKeyboard
     * @attr ref android.R.styleable#Keyboard_Key_popupCharacters
     * @attr ref android.R.styleable#Keyboard_Key_keyOutputText
     * @attr ref android.R.styleable#Keyboard_Key_keyEdgeFlags
     */
    inner class Key(res: Resources, parser: XmlResourceParser, parentRow: Row) {

        /** Width of the key, not including the gap */
        private val keyWidth: Int

        /** Height of the key, not including the gap */
        private val keyHeight: Int

        /** Key horizontal gap before this key. */
        private val keyHorizontalGap: Int

        /**
         * All the key codes (unicode or custom code) that this key could generate,
         * zeroth being the most important.
         */
        private var keyCodes: IntArray? = null

        /**
         * If this key pops up a mini Keyboard,
         * this is the resource id for the XML layout for that Keyboard.
         */
        private val popupKeyboardResId: Int

        /** Popup characters  */
        private val popupKeyboardChars: CharSequence

        /**
         * Flags that specify the anchoring to edges of the Keyboard for detecting touch events
         * that are just out of the boundary of the key.
         *
         * Possible values that can be assigned are {@link Keyboard#ROW_EDGE_TOP ROW_EDGE_LEFT}
         * and {@link Keyboard#ROW_EDGE_BOTTOM ROW_EDGE_BOTTOM}
         */
        private val keyEdgeFlags: Int

        /** Whether this is a modifier key, such as Shift or Alt  */
        private val modifier: Boolean

        /** Whether this key is sticky, i.e., a toggle key  */
        private val sticky: Boolean

        /** Whether this key repeats itself when held down  */
        private val repeatable: Boolean

        /** Preview version of the icon, for the preview popup  */
        private val iconPreview: Drawable?

        /** Text to output when pressed. This can be multiple characters, like ".com"  */
        private val keyOutputText: CharSequence

        /** Label to display  */
        private val keyLabel: CharSequence

        /** Icon to display instead of a label. Icon takes precedence over a label  */
        private val keyIcon: Drawable?

        init {
            parser.require(XmlPullParser.START_DOCUMENT, null, TAG_KEY)
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
                keyCodes = intArrayOf(keyCodesTypedVal.data)
            } else if (keyCodesTypedVal.type == TypedValue.TYPE_STRING) {
                keyCodes = keyCodesTypedVal.string.toString().parseCSV()
            }
            popupKeyboardResId = ta.getResourceId(
                R.styleable.Keyboard_Key_popupKeyboard,
                0
            )
            popupKeyboardChars = ta.getText(R.styleable.Keyboard_Key_popupCharacters)
            keyEdgeFlags = ta.getInt(
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
            repeatable = ta.getBoolean(R.styleable.Keyboard_Key_isRepeatable, false)
            iconPreview = ta.getDrawable(R.styleable.Keyboard_Key_iconPreview)
            iconPreview?.apply {
                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            }
            keyOutputText = ta.getText(R.styleable.Keyboard_Key_keyOutputText)
            keyLabel = ta.getText(R.styleable.Keyboard_Key_keyLabel)
            keyIcon = ta.getDrawable(R.styleable.Keyboard_Key_keyIcon)
            keyIcon?.apply {
                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            }
            if (keyCodes == null && keyLabel.isNotEmpty()) {
                keyCodes = intArrayOf(keyLabel[0].code)
            }
            ta.recycle()
        }
    }
}
