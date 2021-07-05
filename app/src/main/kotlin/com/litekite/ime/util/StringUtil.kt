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
package com.litekite.ime.util

import com.litekite.ime.app.ImeApp
import java.util.StringTokenizer
import java.util.regex.Pattern

/**
 * @author Vignesh S
 * @version 1.0, 15/06/2021
 * @since 1.0
 */
object StringUtil {

    private val TAG = StringUtil::class.java.simpleName

    private val PUNCTUATION_PATTERN = Pattern.compile("[_\\-,.]")

    fun String.parseCSV(): IntArray {
        val size = this.filter { char -> char == ',' }.count()
        val keyCodes = IntArray(size)
        val tokenizer = StringTokenizer(this, ",")
        val index = 0
        while (tokenizer.hasMoreTokens()) {
            try {
                keyCodes[index] = tokenizer.nextToken().toInt()
                index.inc()
            } catch (e: NumberFormatException) {
                ImeApp.printLog(TAG, "Error parsing keycodes $this")
            }
        }
        return keyCodes
    }

    fun String.isPunctuation(): Boolean = PUNCTUATION_PATTERN.matcher(this).matches()
}
