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
package com.litekite.ime.service

import android.inputmethodservice.InputMethodService
import android.os.LocaleList
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import com.litekite.ime.app.ImeApp
import com.litekite.ime.databinding.LayoutKeyboardViewBinding
import com.litekite.ime.util.ContextUtil.themeContext
import com.litekite.ime.widget.Keyboard
import com.litekite.ime.widget.KeyboardView
import java.util.Locale

/**
 * @author Vignesh S
 * @version 1.0, 01/06/2021
 * @since 1.0
 */
class ImeService : InputMethodService() {

    companion object {

        private val TAG: String = ImeService::class.java.simpleName

        const val DEFAULT_LOCALE = "en"
    }

    private lateinit var qwertyKeyboard: Keyboard
    private lateinit var symbolKeyboard: Keyboard

    private var binding: LayoutKeyboardViewBinding? = null

    private val keyboardActionListener = object : KeyboardView.KeyboardActionListener {

        override fun onKey(primaryCode: Int) {
            ImeApp.printLog(TAG, "onKey: $primaryCode")
            val binding = this@ImeService.binding ?: return
            when (primaryCode) {
                Keyboard.KEYCODE_SHIFT -> {
                    // Toggle Capitalization
                    binding.vKeyboard.setShifted(!binding.vKeyboard.isShifted())
                }
            }
        }

        override fun onStopInput() {
            requestHideSelf(0)
        }
    }

    init {
        ImeApp.printLog(ImeApp.TAG, "init:")
    }

    override fun onCreate() {
        super.onCreate()
        ImeApp.printLog(ImeApp.TAG, "onCreate:")
        qwertyKeyboard = createKeyboard(Keyboard.LAYOUT_KEYBOARD_QWERTY)
        symbolKeyboard = createKeyboard(Keyboard.LAYOUT_KEYBOARD_SYMBOL)
    }

    private fun createKeyboard(layoutXml: String): Keyboard {
        val overrideConfig = resources.configuration
        // Set default locale
        val localeList = LocaleList(Locale(DEFAULT_LOCALE))
        overrideConfig.setLocales(localeList)
        // Update configuration
        createConfigurationContext(overrideConfig)
        // Keyboard layout
        return Keyboard(
            this,
            resources.getIdentifier(layoutXml, Keyboard.DEF_TYPE, packageName)
        )
    }

    override fun onCreateInputView(): View {
        ImeApp.printLog(ImeApp.TAG, "onCreateInputView:")
        binding = LayoutKeyboardViewBinding.inflate(LayoutInflater.from(themeContext()))
        return binding!!.root
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        ImeApp.printLog(ImeApp.TAG, "onStartInputView:")
        val binding = this.binding ?: return
        binding.vKeyboard.setKeyboard(qwertyKeyboard)
        binding.vKeyboard.addCallback(keyboardActionListener)
        binding.vKeyboard.setShifted(info.initialCapsMode != 0)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        binding?.vKeyboard?.removeCallback(keyboardActionListener)
        binding = null
        super.onDestroy()
        ImeApp.printLog(ImeApp.TAG, "onDestroy:")
    }
}
