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
import com.google.android.material.color.MaterialColors
import com.litekite.ime.R
import com.litekite.ime.databinding.WidgetKeyPopupPreviewBinding
import com.litekite.ime.util.StringUtil.isPunctuation
import java.util.Locale

/**
 * A Keyboard key preview popup shown above the key like a glance preview of a typed key.
 *
 * @author Vignesh S
 * @version 1.0, 14/07/2021
 * @since 1.0
 */
class KeyPreviewPopupWindow @JvmOverloads constructor(
    private val context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PopupWindow(context, attrs, defStyleAttr) {

    companion object {
        private const val PREVIEW_POPUP_DISMISS_DELAY = 70L
    }

    private val binding = WidgetKeyPopupPreviewBinding.inflate(LayoutInflater.from(context))

    init {
        contentView = binding.root
        contentView.background.setTint(
            MaterialColors.getColor(contentView, R.attr.colorControlHighlight)
        )
        elevation = 10.0F
        isTouchable = false
        animationStyle = android.R.style.Animation_Dialog
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun getLocale(): Locale = context.resources.configuration.locales[0]

    fun showPreview(parent: View, key: Keyboard.Key) {
        val keyLabel = key.adjustLabelCase(getLocale())
        if (keyLabel.isEmpty() || keyLabel.length > 3) {
            // Key icon or special key code preview is disabled
            return
        }
        // Key preview label
        binding.tvKeyPreview.text = keyLabel
        // For punctuation, use large font. For labels, use small font.
        if (keyLabel.isPunctuation()) {
            binding.tvKeyPreview.textSize = context.resources.getDimensionPixelSize(
                R.dimen.keyboard_view_key_punctuation_text_size
            ).toFloat()
        } else {
            binding.tvKeyPreview.textSize = context.resources.getDimensionPixelSize(
                R.dimen.keyboard_view_key_text_size
            ).toFloat()
        }
        // Width & height of the popup window
        width = key.width
        height = key.height * 2
        // Horizontal & vertical padding of the container
        val containerHPadding = ((parent.parent as ViewGroup).width - parent.width) / 2
        val containerVPadding = ((parent.parent as ViewGroup).height - parent.height) / 2
        // Showing popup relative to the key x & y position
        showAtLocation(
            parent,
            Gravity.NO_GRAVITY,
            key.x + parent.paddingLeft + containerHPadding,
            key.y + parent.paddingTop + containerVPadding - key.height
        )
        // Transparent background for the popup decor view
        (contentView.parent.parent as ViewGroup).setBackgroundColor(Color.TRANSPARENT)
    }

    fun hidePreview() {
        if (isShowing) {
            contentView.postDelayed({ dismiss() }, PREVIEW_POPUP_DISMISS_DELAY)
        }
    }
}
