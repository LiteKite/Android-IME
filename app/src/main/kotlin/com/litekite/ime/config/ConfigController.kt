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
package com.litekite.ime.config

import android.content.Context
import android.content.res.Configuration
import com.litekite.ime.base.CallbackProvider
import java.util.ArrayList
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author Vignesh S
 * @version 1.0, 19/07/2021
 * @since 1.0
 */
@Singleton
class ConfigController @Inject constructor(context: Context) :
    CallbackProvider<ConfigController.Callback> {

    companion object {
        /** Constant from [android.content.pm.ActivityInfo] to detect overlay change */
        private const val CONFIG_ASSETS_PATHS = -0x80000000
    }

    private var lastConfig: Configuration = context.resources.configuration
    private var uiMode: Int
    private var orientation: Int

    override val callbacks: ArrayList<Callback> = ArrayList()

    init {
        uiMode = lastConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        orientation = lastConfig.orientation
    }

    fun onConfigChanged(newConfig: Configuration) {
        // Configuration change
        callbacks.forEach { it.onConfigChanged(newConfig) }
        // Density or font scale change
        if (lastConfig.densityDpi != newConfig.densityDpi ||
            lastConfig.fontScale != newConfig.fontScale
        ) {
            callbacks.forEach { it.onDensityOrFontScaleChanged() }
        }
        // Locale change
        if (lastConfig.locales != newConfig.locales) {
            callbacks.forEach { it.onLocaleChanged() }
        }
        // Theme change
        val newUiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (uiMode != newUiMode) {
            callbacks.forEach { it.onThemeChanged() }
            uiMode = newUiMode
        }
        // Device orientation change
        if (orientation != newConfig.orientation) {
            callbacks.forEach { it.onDeviceOrientationChanged() }
            orientation = newConfig.orientation
        }
        // Overlay change
        if ((lastConfig.updateFrom(newConfig) and CONFIG_ASSETS_PATHS) != 0) {
            callbacks.forEach { it.onOverlayChanged() }
        }
    }

    interface Callback {

        fun onConfigChanged(newConfig: Configuration) {}

        fun onDensityOrFontScaleChanged() {}

        fun onLocaleChanged() {}

        fun onThemeChanged() {}

        fun onOverlayChanged() {}

        fun onDeviceOrientationChanged() {}
    }
}
