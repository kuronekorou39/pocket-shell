package com.rou39.pocketshell.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * アプリの配色。アイコンの深緑の地とミントの「>_」に合わせた、端末の画面のような暗い配色だけを持つ。
 * 背景の色は res/values/themes.xml の windowBackground と揃える（起動直後のちらつきを防ぐ）。
 */
private val PocketColors = darkColorScheme(
    primary = Color(0xFFB8F2C2),
    onPrimary = Color(0xFF0E2A18),
    primaryContainer = Color(0xFF2E4A3A),
    onPrimaryContainer = Color(0xFFD4F8DB),
    secondary = Color(0xFF9CCFAA),
    onSecondary = Color(0xFF0E2A18),
    secondaryContainer = Color(0xFF26392F),
    onSecondaryContainer = Color(0xFFCBE9D3),
    tertiary = Color(0xFFE6C27A),
    onTertiary = Color(0xFF3E2C00),
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
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0907),
)

/** 成功・失敗の印の色。 */
val OkColor = Color(0xFF8BD8A0)
val NgColor = Color(0xFFFF8A80)

/** コマンドの出力を載せる面。地より一段暗くして、出力だと分かるようにする。 */
val OutputBackground = Color(0xFF0B100D)

@Composable
fun PocketTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PocketColors, content = content)
}
