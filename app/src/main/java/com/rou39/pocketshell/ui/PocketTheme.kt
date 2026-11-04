package com.rou39.pocketshell.ui

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

/** アプリの配色。設定の「色」で選ぶ。既定は黒地にミントの、端末の画面のような配色。 */
enum class ThemePalette(val label: String) {
    TERMINAL("ターミナル"),
    FOREST("深緑"),
    AMBER("琥珀"),
    NAVY("紺"),
    WALLPAPER("壁紙の色"),
    LIGHT("明るい"),
}

fun ColorScheme.isLight(): Boolean = background.luminance() > 0.5f

fun colorSchemeFor(palette: ThemePalette, context: Context): ColorScheme = when (palette) {
    ThemePalette.TERMINAL -> Terminal
    ThemePalette.FOREST -> Forest
    ThemePalette.AMBER -> Amber
    ThemePalette.NAVY -> Navy
    ThemePalette.WALLPAPER -> dynamicDarkColorScheme(context)
    ThemePalette.LIGHT -> Light
}

/** 成功・失敗の印の色。明るい配色では濃くして読めるようにする。 */
val ColorScheme.ok: Color get() = if (isLight()) Color(0xFF1E7A3C) else Color(0xFF8BD8A0)
val ColorScheme.ng: Color get() = if (isLight()) Color(0xFFB3261E) else Color(0xFFFF8A80)

/** コマンドの出力を載せる面。地より一段沈めて、出力だと分かるようにする。 */
val ColorScheme.output: Color get() = surfaceContainerLowest

@Composable
fun PocketTheme(palette: ThemePalette, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorSchemeFor(palette, LocalContext.current), content = content)
}

private val Terminal = darkColorScheme(
    primary = Color(0xFF9EE6B0),
    onPrimary = Color(0xFF0B2A16),
    primaryContainer = Color(0xFF23382B),
    onPrimaryContainer = Color(0xFFC8F5D3),
    secondary = Color(0xFF9EE6B0),
    onSecondary = Color(0xFF0B2A16),
    secondaryContainer = Color(0xFF23382B),
    onSecondaryContainer = Color(0xFFC8F5D3),
    background = Color(0xFF101211),
    onBackground = Color(0xFFE4E8E5),
    surface = Color(0xFF101211),
    onSurface = Color(0xFFE4E8E5),
    surfaceVariant = Color(0xFF262A28),
    onSurfaceVariant = Color(0xFFA3ABA6),
    surfaceContainerLowest = Color(0xFF0A0B0A),
    surfaceContainerLow = Color(0xFF151816),
    surfaceContainer = Color(0xFF1A1D1B),
    surfaceContainerHigh = Color(0xFF232725),
    surfaceContainerHighest = Color(0xFF2B302D),
    outline = Color(0xFF6E7671),
    outlineVariant = Color(0xFF2F3431),
)

private val Forest = darkColorScheme(
    primary = Color(0xFFB8F2C2),
    onPrimary = Color(0xFF0E2A18),
    primaryContainer = Color(0xFF2E4A3A),
    onPrimaryContainer = Color(0xFFD4F8DB),
    secondary = Color(0xFF9CCFAA),
    onSecondary = Color(0xFF0E2A18),
    secondaryContainer = Color(0xFF26392F),
    onSecondaryContainer = Color(0xFFCBE9D3),
    background = Color(0xFF111714),
    onBackground = Color(0xFFE2EAE4),
    surface = Color(0xFF111714),
    onSurface = Color(0xFFE2EAE4),
    surfaceVariant = Color(0xFF2A3530),
    onSurfaceVariant = Color(0xFFA7B5AC),
    surfaceContainerLowest = Color(0xFF0B100D),
    surfaceContainerLow = Color(0xFF151C18),
    surfaceContainer = Color(0xFF1A221E),
    surfaceContainerHigh = Color(0xFF222C27),
    surfaceContainerHighest = Color(0xFF2A3530),
    outline = Color(0xFF6F7D74),
    outlineVariant = Color(0xFF34403A),
)

private val Amber = darkColorScheme(
    primary = Color(0xFFFFC266),
    onPrimary = Color(0xFF3A2600),
    primaryContainer = Color(0xFF4A3510),
    onPrimaryContainer = Color(0xFFFFE0B0),
    secondary = Color(0xFFFFC266),
    onSecondary = Color(0xFF3A2600),
    secondaryContainer = Color(0xFF4A3510),
    onSecondaryContainer = Color(0xFFFFE0B0),
    background = Color(0xFF120F0A),
    onBackground = Color(0xFFF0E6D6),
    surface = Color(0xFF120F0A),
    onSurface = Color(0xFFF0E6D6),
    surfaceVariant = Color(0xFF2E271C),
    onSurfaceVariant = Color(0xFFBFAF96),
    surfaceContainerLowest = Color(0xFF0A0805),
    surfaceContainerLow = Color(0xFF18140D),
    surfaceContainer = Color(0xFF1E1910),
    surfaceContainerHigh = Color(0xFF282114),
    surfaceContainerHighest = Color(0xFF322A1B),
    outline = Color(0xFF85776A),
    outlineVariant = Color(0xFF3C3226),
)

private val Navy = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF102060),
    primaryContainer = Color(0xFF2F4690),
    onPrimaryContainer = Color(0xFFDCE1FF),
    secondary = Color(0xFFE6C27A),
    onSecondary = Color(0xFF3E2C00),
    secondaryContainer = Color(0xFF2F4690),
    onSecondaryContainer = Color(0xFFDCE1FF),
    background = Color(0xFF0F1320),
    onBackground = Color(0xFFE4E5EF),
    surface = Color(0xFF0F1320),
    onSurface = Color(0xFFE4E5EF),
    surfaceVariant = Color(0xFF2A3043),
    onSurfaceVariant = Color(0xFFB4B8CA),
    surfaceContainerLowest = Color(0xFF0A0D17),
    surfaceContainerLow = Color(0xFF151A29),
    surfaceContainer = Color(0xFF1A1F30),
    surfaceContainerHigh = Color(0xFF22283B),
    surfaceContainerHighest = Color(0xFF2B3146),
    outline = Color(0xFF7E8399),
    outlineVariant = Color(0xFF3A4057),
)

private val Light = lightColorScheme(
    primary = Color(0xFF1F6B3A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC6EED2),
    onPrimaryContainer = Color(0xFF0A2814),
    secondary = Color(0xFF1F6B3A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC6EED2),
    onSecondaryContainer = Color(0xFF0A2814),
    background = Color(0xFFF6F7F5),
    onBackground = Color(0xFF1A1D1B),
    surface = Color(0xFFF6F7F5),
    onSurface = Color(0xFF1A1D1B),
    surfaceVariant = Color(0xFFDDE2DE),
    onSurfaceVariant = Color(0xFF4A524D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F2EF),
    surfaceContainer = Color(0xFFECEEEB),
    surfaceContainerHigh = Color(0xFFE5E8E4),
    surfaceContainerHighest = Color(0xFFDFE2DE),
    outline = Color(0xFF79817C),
    outlineVariant = Color(0xFFC7CCC8),
)
