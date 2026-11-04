package com.rou39.pocketshell.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/** 登録したコマンド。ボタン1つで実行する。 */
@Serializable
data class Recipe(val id: String = UUID.randomUUID().toString(), val name: String, val command: String)

private val Context.store by preferencesDataStore(name = "recipes")

/** レシピの保存。一覧をまとめて JSON で持つ（件数は多くならない想定）。 */
class RecipeStore(private val context: Context) {
    private val key = stringPreferencesKey("recipes")
    private val json = Json { ignoreUnknownKeys = true }

    /** まだ一度も保存していなければ、例のレシピを出す。 */
    val recipes: Flow<List<Recipe>> = context.store.data.map { p ->
        p[key]?.let { runCatching { json.decodeFromString<List<Recipe>>(it) }.getOrNull() } ?: EXAMPLES
    }

    suspend fun save(recipe: Recipe) = update { list ->
        if (list.any { it.id == recipe.id }) list.map { if (it.id == recipe.id) recipe else it } else list + recipe
    }

    suspend fun delete(id: String) = update { list -> list.filterNot { it.id == id } }

    suspend fun move(id: String, offset: Int) = update { list ->
        val from = list.indexOfFirst { it.id == id }
        val to = from + offset
        if (from < 0 || to !in list.indices) list else list.toMutableList().apply { add(to, removeAt(from)) }
    }

    private suspend fun update(transform: (List<Recipe>) -> List<Recipe>) {
        context.store.edit { p ->
            val current = p[key]?.let { runCatching { json.decodeFromString<List<Recipe>>(it) }.getOrNull() } ?: EXAMPLES
            p[key] = json.encodeToString(transform(current))
        }
    }

    private companion object {
        /** 最初に出す例。誰の端末でも意味があるものだけにする（関連アプリ用のコマンドは RelatedApps に置く）。 */
        val EXAMPLES = listOf(
            Recipe("example-whoami", "シェル権限の確認", "id"),
            Recipe("example-battery", "電池の状態", "dumpsys battery"),
            Recipe("example-screen", "画面の解像度と密度", "wm size; wm density"),
        )
    }
}
