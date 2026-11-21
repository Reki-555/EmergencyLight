package com.emergencylight.app.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emergencylight.app.R

/** Colors that never change with Style or Theme (section 9.2). */
object FixedColors {
    val SosRed = Color(0xFFD50000)
    val SosActiveA = Color(0xFFFFFFFF)
    val SosActiveB = Color(0xFFFFE3E3)
    val ControlBarBg = Color(0x99000000) // black, 60% opacity
    val Check = Color(0xFF000000)
    val ColorCircleBorder = Color(0xFF808080)
    val White = Color(0xFFFFFFFF)
}

enum class StyleId(val id: String, @StringRes val label: Int) {
    MINIMAL("minimal", R.string.style_minimal),
    DASHBOARD("dashboard", R.string.style_dashboard),
    INSTRUMENT("instrument", R.string.style_instrument),
    PARCHMENT("parchment", R.string.style_parchment),
    STICKER("sticker", R.string.style_sticker);

    companion object {
        fun fromId(id: String?): StyleId = entries.firstOrNull { it.id == id } ?: MINIMAL
    }
}

/** One color set (section 9.3). bgBottom differs from bg only for the parchment gradient. */
data class Palette(
    val bg: Color,
    val bgBottom: Color,
    val surface: Color,
    val text: Color,
    val text2: Color,
    val accent: Color,
    val onAccent: Color,
    val outline: Color,
    val grid: Color? = null,
)

private fun c(hex: Long) = Color(hex)

private fun pal(
    bg: Long, surface: Long, text: Long, text2: Long, accent: Long, onAccent: Long, outline: Long,
    grid: Long? = null, bgBottom: Long? = null,
) = Palette(
    bg = c(bg), bgBottom = c(bgBottom ?: bg), surface = c(surface), text = c(text), text2 = c(text2),
    accent = c(accent), onAccent = c(onAccent), outline = c(outline), grid = grid?.let { c(it) },
)

/** The 10 color sets. These values pass the contrast rules; do not change them. */
fun paletteFor(id: StyleId, dark: Boolean): Palette = when (id) {
    StyleId.MINIMAL -> if (dark) {
        pal(0xFF121212, 0xFF1E1E1E, 0xFFF2F2F2, 0xFFB8B8B8, 0xFFFFB300, 0xFF000000, 0xFF757575)
    } else {
        pal(0xFFF5F5F5, 0xFFFFFFFF, 0xFF1A1A1A, 0xFF555555, 0xFFB54700, 0xFFFFFFFF, 0xFF8A8A8A)
    }
    StyleId.DASHBOARD -> if (dark) {
        pal(0xFF05080D, 0xFF0E1520, 0xFFE6F4FF, 0xFF8FB3CC, 0xFF29B6F6, 0xFF000000, 0xFF4F7699)
    } else {
        pal(0xFFDDE3EA, 0xFFF4F7FA, 0xFF0D1B2A, 0xFF3D5468, 0xFF075E8F, 0xFFFFFFFF, 0xFF7D8FA1)
    }
    StyleId.INSTRUMENT -> if (dark) {
        pal(0xFF141C18, 0xFF1C2621, 0xFFD8F5E1, 0xFF93B3A0, 0xFF5CFF8A, 0xFF000000, 0xFF638571, grid = 0xFF22302A)
    } else {
        pal(0xFFE7EBE5, 0xFFF4F6F2, 0xFF18261D, 0xFF4A5C50, 0xFF1A6E35, 0xFFFFFFFF, 0xFF7F9184, grid = 0xFFD5DCD3)
    }
    StyleId.PARCHMENT -> if (dark) {
        pal(0xFF1F150E, 0xFF2B1D14, 0xFFF0DDB0, 0xFFC9AE7C, 0xFFE8C77A, 0xFF000000, 0xFF957253)
    } else {
        pal(0xFFE9D8B4, 0xFFF3E6C8, 0xFF3B2412, 0xFF6B4A2E, 0xFF8B1A1A, 0xFFFFFFFF, 0xFF8C6E45, bgBottom = 0xFFDCC69C)
    }
    StyleId.STICKER -> if (dark) {
        pal(0xFF1C2340, 0xFF2A3358, 0xFFFFF8EC, 0xFFC9CBE8, 0xFFFFD166, 0xFF000000, 0xFFE6E8FF)
    } else {
        pal(0xFFFFF4E3, 0xFFFFFFFF, 0xFF2B2240, 0xFF5E5470, 0xFFC2185B, 0xFFFFFFFF, 0xFF2B2240)
    }
}

/** Everything that makes a Style look different (section 9.4). */
data class AppStyle(
    val id: StyleId,
    val dark: Boolean,
    val palette: Palette,
    val bodyFont: FontFamily,
    val numberFont: FontFamily,
    val headerFont: FontFamily,
    val cardCorner: Dp,
    val uppercaseHeaders: Boolean,
    val headerLetterSpacing: TextUnit,
)

fun appStyleFor(id: StyleId, dark: Boolean): AppStyle {
    val p = paletteFor(id, dark)
    return when (id) {
        StyleId.MINIMAL -> AppStyle(id, dark, p, FontFamily.SansSerif, FontFamily.SansSerif, FontFamily.SansSerif, 16.dp, false, TextUnit.Unspecified)
        StyleId.DASHBOARD -> AppStyle(id, dark, p, FontFamily.SansSerif, FontFamily.Monospace, FontFamily.SansSerif, 12.dp, true, 1.sp)
        StyleId.INSTRUMENT -> AppStyle(id, dark, p, FontFamily.Monospace, FontFamily.Monospace, FontFamily.Monospace, 4.dp, true, TextUnit.Unspecified)
        StyleId.PARCHMENT -> AppStyle(id, dark, p, FontFamily.Serif, FontFamily.Serif, FontFamily.Serif, 6.dp, false, TextUnit.Unspecified)
        StyleId.STICKER -> AppStyle(id, dark, p, FontFamily.SansSerif, FontFamily.SansSerif, FontFamily.SansSerif, 20.dp, false, TextUnit.Unspecified)
    }
}

val LocalAppStyle = staticCompositionLocalOf { appStyleFor(StyleId.MINIMAL, true) }

/** All Material text styles are 16sp or bigger (no text smaller than 16sp anywhere). */
private fun typographyFor(font: FontFamily): Typography {
    fun s(size: Int) = TextStyle(fontFamily = font, fontSize = size.sp)
    return Typography(
        displayLarge = s(32), displayMedium = s(30), displaySmall = s(28),
        headlineLarge = s(28), headlineMedium = s(26), headlineSmall = s(24),
        titleLarge = s(22), titleMedium = s(20), titleSmall = s(18),
        bodyLarge = s(18), bodyMedium = s(18), bodySmall = s(16),
        labelLarge = s(18), labelMedium = s(16), labelSmall = s(16),
    )
}

@Composable
fun AppTheme(style: AppStyle, content: @Composable () -> Unit) {
    val p = style.palette
    val scheme = if (style.dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.accent, onSecondary = p.onAccent,
            background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
            surfaceVariant = p.surface, onSurfaceVariant = p.text2, outline = p.outline,
            surfaceContainerLow = p.surface, surfaceContainer = p.surface, surfaceContainerHigh = p.surface,
            surfaceContainerHighest = p.surface, inverseSurface = p.text, inverseOnSurface = p.surface,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, secondary = p.accent, onSecondary = p.onAccent,
            background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
            surfaceVariant = p.surface, onSurfaceVariant = p.text2, outline = p.outline,
            surfaceContainerLow = p.surface, surfaceContainer = p.surface, surfaceContainerHigh = p.surface,
            surfaceContainerHighest = p.surface, inverseSurface = p.text, inverseOnSurface = p.surface,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = typographyFor(style.bodyFont)) {
        CompositionLocalProvider(LocalAppStyle provides style) {
            content()
        }
    }
}
