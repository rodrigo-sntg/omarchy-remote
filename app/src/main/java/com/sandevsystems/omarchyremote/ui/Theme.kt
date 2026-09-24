package com.sandevsystems.omarchyremote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

/** Design v2: graphite, one accent, Archivo everywhere (tokens in DesignTokens.kt). */
private val typography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontFamily = Archivo),
        bodyMedium = bodyMedium.copy(fontFamily = Archivo),
        bodySmall = bodySmall.copy(fontFamily = Archivo),
        labelLarge = labelLarge.copy(fontFamily = Archivo),
        labelMedium = labelMedium.copy(fontFamily = Archivo),
        labelSmall = labelSmall.copy(fontFamily = Archivo),
        titleLarge = titleLarge.copy(fontFamily = Archivo),
        titleMedium = titleMedium.copy(fontFamily = Archivo),
        titleSmall = titleSmall.copy(fontFamily = Archivo),
    )
}

@Composable
fun KeypadTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = keypadColorScheme(), typography = typography, content = content)
