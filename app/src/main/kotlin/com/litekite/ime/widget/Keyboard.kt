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
import android.content.res.XmlResourceParser
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
 *         android:horizontalGap="2px"
 *         android:verticalGap="2px" &gt;
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
 * @attr ref android.R.styleable#Keyboard_horizontalGap
 * @attr ref android.R.styleable#Keyboard_verticalGap
 *
 * @author Vignesh S
 * @version 1.0, 14/06/2021
 * @since 1.0
 */
class Keyboard(context: Context, layoutRes: Int) {

    companion object {
        const val TAG_KEYBOARD = "keyboard"
        const val TAG_ROW = "Row"
        const val TAG_Key = "Key"
    }

    init {
        loadKeyboard(context, context.resources.getXml(layoutRes))
    }

    private fun loadKeyboard(context: Context, parser: XmlResourceParser) {
        parser.require(XmlPullParser.START_DOCUMENT, null, TAG_KEYBOARD)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    TAG_ROW -> {
                    }
                    TAG_Key -> {
                    }
                }
            }
        }
    }
}
