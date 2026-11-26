package com.cybertube.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.cybertube.app.R

/** The two switchable looks ("ทีม"). [accent] is fixed per team so the picker can show both at once. */
enum class Team(val key: String, val label: String, val desc: String, val accent: Color) {
    GREEN("green", "ทีมเขียวเรืองแสง", "ธีมสีเขียวสุดเท่", Color(0xFF2EF84A)),
    BLUE("blue", "ทีมสีน้ำเงินเรืองแสง", "ธีมสีน้ำเงินสุดเท่", Color(0xFF1E9BFF));

    @get:DrawableRes
    val logoRes: Int get() = if (this == GREEN) R.drawable.logo_green else R.drawable.logo_blue

    @get:DrawableRes
    val bgRes: Int get() = if (this == GREEN) R.drawable.bg_settings_green else R.drawable.bg_settings_blue

    companion object {
        val DEFAULT = BLUE
        fun fromKey(k: String?): Team = values().firstOrNull { it.key == k } ?: DEFAULT
    }
}

private class Palette(
    val bg: Color, val surface: Color, val surface2: Color,
    val main: Color, val second: Color, val text: Color, val dim: Color,
)

private val GreenPalette = Palette(
    bg = Color(0xFF05070A), surface = Color(0xFF0A110C), surface2 = Color(0xFF111B14),
    main = Color(0xFF2EF84A), second = Color(0xFF8DFFA3), text = Color(0xFFE2FFE8), dim = Color(0xFF769881),
)

private val BluePalette = Palette(
    bg = Color(0xFF05070C), surface = Color(0xFF0A1019), surface2 = Color(0xFF111B2B),
    main = Color(0xFF1E9BFF), second = Color(0xFF00E5FF), text = Color(0xFFE6F2FF), dim = Color(0xFF7D95B0),
)

/**
 * App colors. Every colour is read from the current [team], which is Compose state, so changing the
 * team recomposes the whole UI instantly. (`green` is the team's main colour: green or blue.)
 */
object Cy {
    val team = mutableStateOf(Team.DEFAULT)

    private val p: Palette get() = if (team.value == Team.GREEN) GreenPalette else BluePalette

    val bg: Color get() = p.bg
    val surface: Color get() = p.surface
    val surface2: Color get() = p.surface2
    val green: Color get() = p.main
    val cyan: Color get() = p.second
    val text: Color get() = p.text
    val dim: Color get() = p.dim
    val magenta = Color(0xFFFF2BD6)
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
