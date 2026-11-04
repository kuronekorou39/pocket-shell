package com.rou39.pocketshell.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rou39.pocketshell.ui.ThemePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settings by preferencesDataStore(name = "settings")

/** 画面の設定。今は配色だけ。 */
class SettingsStore(private val context: Context) {
    private val paletteKey = stringPreferencesKey("palette")

    val palette: Flow<ThemePalette> = context.settings.data.map { p ->
        p[paletteKey]?.let { name -> ThemePalette.entries.firstOrNull { it.name == name } } ?: ThemePalette.TERMINAL
    }

    suspend fun setPalette(palette: ThemePalette) {
        context.settings.edit { it[paletteKey] = palette.name }
    }
}
