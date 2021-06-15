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
import android.util.Xml
import com.litekite.ime.R
import com.litekite.ime.util.DimensUtil.getDimensionOrFraction
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
        const val TAG_Key = "Key"
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
    private val defaultKeyWidth: Int = displayWidth / 10

    /** key default height */
    private val defaultKeyHeight: Int = defaultKeyWidth

    /** Key horizontal default gap for all rows */
    private val defaultKeyHorizontalGap = 0

    /** Key vertical default gap between rows */
    private val defaultKeyVerticalGap = 0

    private val rows: ArrayList<Row> = ArrayList()

    init {
        // Parses Keyboard attributes
        loadKeyboard(context, context.resources.getXml(layoutRes))
    }

    private fun loadKeyboard(context: Context, parser: XmlResourceParser) {
        parser.require(XmlPullParser.START_DOCUMENT, null, TAG_KEYBOARD)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    TAG_KEYBOARD -> {
                    }
                    TAG_ROW -> {
                        rows.add(Row(context.resources, parser))
                    }
                    TAG_Key -> {
                    }
                }
            }
        }
    }

    inner class Key

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
        private val rowEdgeFlags: Int

        /** The Keyboard mode for this row  */
        private val mode: Int

        private val keys: ArrayList<Key> = ArrayList()

        init {
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
            ta = res.obtainAttributes(
                Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Row
            )
            rowEdgeFlags = ta.getInt(
                R.styleable.Keyboard_Row_rowEdgeFlags,
                0
            )
            mode = ta.getResourceId(
                R.styleable.Keyboard_Row_keyboardMode,
                0
            )
            ta.recycle()
        }
    }
}
