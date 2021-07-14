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

import java.util.Locale

/**
 * @author Vignesh S
 * @version 1.0, 14/07/2021
 * @since 1.0
 */
object CharUtil {

    /**
     * Cycle through alternate characters of the given character. Return the same character if
     * there is no alternate.
     */
    fun Char.cycleCharacter(locale: Locale): Char {
        return if (Character.isUpperCase(this)) {
            this.lowercase(locale)[0]
        } else {
            this.uppercase(locale)[0]
        }
    }
}
