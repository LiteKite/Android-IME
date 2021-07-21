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
import com.google.android.material.color.MaterialColors
import com.litekite.ime.R
import com.litekite.ime.app.ImeApp
import com.litekite.ime.config.ConfigController
import com.litekite.ime.databinding.LayoutKeyboardViewBinding
import com.litekite.ime.util.CharUtil.cycleCharacter
import com.litekite.ime.widget.Keyboard
import com.litekite.ime.widget.KeyboardView
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

/**
 * @author Vignesh S
 * @version 1.0, 01/06/2021
 * @since 1.0
 */
@AndroidEntryPoint
class ImeService : InputMethodService(), ConfigController.Callback {

    companion object {

        private val TAG: String = ImeService::class.java.simpleName

        private const val DEFAULT_LOCALE = "en"
        private const val IME_ACTION_CUSTOM_LABEL = EditorInfo.IME_MASK_ACTION + 1
    }

    @Inject
    lateinit var configController: ConfigController

    private var _editorInfo: EditorInfo? = null
    private val editorInfo: EditorInfo get() = _editorInfo!!

    private lateinit var qwertyKeyboard: Keyboard
    private lateinit var symbolKeyboard: Keyboard

    private var _binding: LayoutKeyboardViewBinding? = null
    private val binding: LayoutKeyboardViewBinding get() = _binding!!

    init {
        ImeApp.printLog(TAG, "init:")
    }

    override fun onCreate() {
        setTheme(R.style.Theme_AndroidIME)
        super.onCreate()
        ImeApp.printLog(TAG, "onCreate:")
        parseKeyboardLayoutFromXml()
        // Setting configuration callback
        configController.addCallback(this)
    }

    override fun onThemeChanged() {
        super.onThemeChanged()
        ImeApp.printLog(TAG, "onThemeChanged:")
        // Applying theme to resolve attributes of the current theme
        theme.applyStyle(R.style.Theme_AndroidIME, true)
        // Changing nav bar background
        window.window?.navigationBarColor = MaterialColors.getColor(
            binding.vKeyboard,
            android.R.attr.navigationBarColor
        )
        // Recreate all the keyboard layouts to reflect theme change
        parseKeyboardLayoutFromXml()
        binding.vKeyboard.setKeyboard(qwertyKeyboard)
    }

    override fun onDeviceOrientationChanged() {
        super.onDeviceOrientationChanged()
        // Recreate all the keyboard layouts to reflect device orientation change
        parseKeyboardLayoutFromXml()
        binding.vKeyboard.setKeyboard(qwertyKeyboard)
    }

    private fun parseKeyboardLayoutFromXml() {
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
        ImeApp.printLog(TAG, "onCreateInputView:")
        _binding = LayoutKeyboardViewBinding.inflate(LayoutInflater.from(this))
        return binding.root
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        ImeApp.printLog(TAG, "onStartInputView:")
        _editorInfo = info
        binding.vKeyboard.setKeyboard(qwertyKeyboard)
        binding.vKeyboard.addCallback(keyboardActionListener)
        binding.vKeyboard.setShifted(info.initialCapsMode != 0)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        ImeApp.printLog(TAG, "onDestroy:")
        // Removing callback
        configController.removeCallback(this)
        binding.vKeyboard.removeCallback(keyboardActionListener)
        _binding = null
        super.onDestroy()
    }

    private val keyboardActionListener = object : KeyboardView.KeyboardActionListener {

        override fun onKey(primaryCode: Int) {
            ImeApp.printLog(TAG, "onKey: $primaryCode")
            when (primaryCode) {
                Keyboard.KEYCODE_SHIFT -> {
                    // Toggle Capitalization
                    binding.vKeyboard.setShifted(!binding.vKeyboard.isShifted())
                }
                Keyboard.KEYCODE_MODE_CHANGE -> {
                    if (binding.vKeyboard.keyboard === qwertyKeyboard) {
                        binding.vKeyboard.setKeyboard(symbolKeyboard)
                    } else {
                        binding.vKeyboard.setKeyboard(qwertyKeyboard)
                    }
                }
                Keyboard.KEYCODE_DONE -> {
                    val action = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
                    currentInputConnection.performEditorAction(action)
                }
                Keyboard.KEYCODE_DELETE -> {
                    currentInputConnection.deleteSurroundingText(1, 0)
                }
                Keyboard.KEYCODE_MAIN_KEYBOARD -> {
                    binding.vKeyboard.setKeyboard(qwertyKeyboard)
                }
                Keyboard.KEYCODE_NUM_KEYBOARD -> {
                    // No number keyboard layout support.
                }
                Keyboard.KEYCODE_ALPHA_KEYBOARD -> {
                    // No alphanumeric keyboard layout support.
                }
                Keyboard.KEYCODE_CLOSE_KEYBOARD -> {
                    requestHideSelf(0)
                }
                Keyboard.KEYCODE_CYCLE_CHAR -> {
                    val text = currentInputConnection.getTextBeforeCursor(1, 0)
                    if (text.isNullOrEmpty()) {
                        return
                    }
                    val currChar = text[0]
                    val altChar = currChar.cycleCharacter(binding.vKeyboard.getLocale())
                    // Don't modify text if there is no alternate.
                    if (currChar != altChar) {
                        currentInputConnection.deleteSurroundingText(1, 0)
                        currentInputConnection.commitText(altChar.toString(), 1)
                    }
                }
                Keyboard.KEYCODE_ENTER -> {
                    val imeOptionsActionId = getImeOptionsActionId(editorInfo)
                    when {
                        IME_ACTION_CUSTOM_LABEL == imeOptionsActionId -> {
                            // Either we have an actionLabel and we should
                            // performEditorAction with actionId regardless of its value.
                            currentInputConnection.performEditorAction(editorInfo.actionId)
                        }
                        EditorInfo.IME_ACTION_NONE != imeOptionsActionId -> {
                            // We didn't have an actionLabel, but we had another action to execute.
                            // EditorInfo.IME_ACTION_NONE explicitly means no action.
                            // In contrast, EditorInfo.IME_ACTION_UNSPECIFIED is the default value
                            // for an action, so it means there should be an action and
                            // the app didn't bother to set a specific code for it
                            // - presumably it only handles one. It does not have to be treated
                            // in any specific way: anything that is not IME_ACTION_NONE
                            // should be sent to performEditorAction.
                            currentInputConnection.performEditorAction(imeOptionsActionId)
                        }
                        else -> {
                            // No action label, and the action from imeOptions is NONE:
                            // this is a regular enter key that should input a carriage return.
                            commitText(primaryCode)
                        }
                    }
                }
                else -> {
                    commitText(primaryCode)
                }
            }
        }

        override fun onStopInput() {
            requestHideSelf(0)
        }
    }

    private fun getImeOptionsActionId(info: EditorInfo): Int {
        return when {
            info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0 -> {
                EditorInfo.IME_ACTION_NONE
            }
            info.actionLabel != null -> {
                IME_ACTION_CUSTOM_LABEL
            }
            else -> {
                // Note: this is different from editorInfo.actionId, hence "ImeOptionsActionId"
                info.imeOptions and EditorInfo.IME_MASK_ACTION
            }
        }
    }

    private fun commitText(code: Int) {
        var commitText = Char(code).toString()
        // Chars always come through as lowercase, so we have to explicitly
        // uppercase them if the keyboard is shifted.
        if (binding.vKeyboard.isShifted()) {
            commitText = commitText.uppercase(binding.vKeyboard.getLocale())
        }
        ImeApp.printLog(TAG, "commitText: $commitText")
        currentInputConnection.commitText(commitText, 1)
    }
}
