package com.cybertube.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily

object Cy {
    val bg = Color(0xFF05070A)
    val surface = Color(0xFF0B0F14)
    val surface2 = Color(0xFF121A22)
    val green = Color(0xFF00FF9C)
    val cyan = Color(0xFF00E5FF)
    val magenta = Color(0xFFFF2BD6)
    val text = Color(0xFFD7FFF0)
    val dim = Color(0xFF6B8A7F)
    val red = Color(0xFFFF4D6D)
}

private val mono = FontFamily.Monospace

private val CyTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontFamily = mono),
        titleLarge = t.titleLarge.copy(fontFamily = mono),
        titleMedium = t.titleMedium.copy(fontFamily = mono),
        titleSmall = t.titleSmall.copy(fontFamily = mono),
        bodyLarge = t.bodyLarge.copy(fontFamily = mono),
        bodyMedium = t.bodyMedium.copy(fontFamily = mono),
        bodySmall = t.bodySmall.copy(fontFamily = mono),
        labelLarge = t.labelLarge.copy(fontFamily = mono),
        labelMedium = t.labelMedium.copy(fontFamily = mono),
        labelSmall = t.labelSmall.copy(fontFamily = mono),
    )
}

@Composable
fun CyberTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cy.green,
            onPrimary = Cy.bg,
            secondary = Cy.cyan,
            tertiary = Cy.magenta,
            background = Cy.bg,
            onBackground = Cy.text,
            surface = Cy.surface,
            onSurface = Cy.text,
            surfaceVariant = Cy.surface2,
            onSurfaceVariant = Cy.dim,
            outline = Cy.dim,
            error = Cy.red,
        ),
        typography = CyTypography,
        content = content,
    )
}
