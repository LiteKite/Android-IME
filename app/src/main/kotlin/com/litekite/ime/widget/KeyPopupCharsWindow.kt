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
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import com.google.android.material.color.MaterialColors
import com.litekite.ime.R
import com.litekite.ime.base.CallbackProvider
import com.litekite.ime.databinding.WidgetKeyPopupCharBinding
import java.util.Locale

/**
 * A Keyboard key characters popup shown above the key that supports multiple character types
 * for a single character or label.
 *
 * @author Vignesh S
 * @version 1.0, 01/08/2021
 * @since 1.0
 */
class KeyPopupCharsWindow @JvmOverloads constructor(
    private val context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PopupWindow(context, attrs, defStyleAttr),
    CallbackProvider<KeyPopupCharsWindow.KeyPopupCharListener> {

    companion object {
        private const val POPUP_CHARS_DISMISS_DELAY = 70L
    }

    override val callbacks: ArrayList<KeyPopupCharListener> = ArrayList()

    init {
        contentView = LinearLayoutCompat(context)
        contentView.background = ContextCompat.getDrawable(
            context,
            R.drawable.bg_keyboard_key
        )
        contentView.background.setTint(
            MaterialColors.getColor(contentView, R.attr.colorControlHighlight)
        )
        elevation = 10.0F
        animationStyle = android.R.style.Animation_Dialog
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun getLocale(): Locale = context.resources.configuration.locales[0]

    fun showPopupChars(parent: View, key: Keyboard.Key) {
        val popupChars = key.popupKeyboardChars
        if (popupChars.isEmpty()) {
            // Key popup chars is disabled if there is no alternate popup chars
            return
        }
        // Clearing existing views
        (contentView as ViewGroup).removeAllViews()
        // Creating and adding popup characters
        for (charIndex in popupChars.indices) {
            val keyBinding = WidgetKeyPopupCharBinding.inflate(LayoutInflater.from(context))
            // Adjusting case based on the main keyboard key
            val keyLabel = key.adjustPopupCharCase(popupChars[charIndex], getLocale())
            keyBinding.tvKeyPopupChar.text = keyLabel
            keyBinding.tvKeyPopupChar.textSize = context.resources.getDimensionPixelSize(
                R.dimen.keyboard_view_key_text_size
            ).toFloat()
            // Adding popup character
            (contentView as ViewGroup).addView(keyBinding.root)
            // Width & height of the popup window
            keyBinding.root.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = key.width
                height = key.height
                if (charIndex != popupChars.lastIndex) {
                    marginEnd = key.verticalGap
                }
            }
            // Listening for click events
            keyBinding.tvKeyPopupChar.setOnClickListener {
                dismiss()
                callbacks.forEach { it.onKey(popupChars[charIndex].code) }
            }
        }
        // Padding for the popup container
        contentView.setPadding(key.verticalGap)
        // Horizontal & vertical padding of the container
        val containerHPadding = ((parent.parent as ViewGroup).width - parent.width) / 2
        val containerVPadding = ((parent.parent as ViewGroup).height - parent.height) / 2
        // Showing popup relative to the key x & y position
        showAtLocation(
            parent,
            Gravity.NO_GRAVITY,
            key.x + parent.paddingLeft + containerHPadding,
            key.y + parent.paddingTop + containerVPadding - key.height - (key.verticalGap * 2)
        )
        // Transparent background for the popup decor view
        (contentView.parent.parent as ViewGroup).setBackgroundColor(Color.TRANSPARENT)
    }

    fun hidePopupChars() {
        if (isShowing) {
            contentView.postDelayed({ dismiss() }, POPUP_CHARS_DISMISS_DELAY)
        }
    }

    /**
     * Listener for keyboard popup character events.
     */
    fun interface KeyPopupCharListener {

        /**
         * Send a key press to the listener.
         * @param primaryCode this is the key that was pressed
         */
        fun onKey(primaryCode: Int)
    }
}
