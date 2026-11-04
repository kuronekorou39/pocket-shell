package com.rou39.pocketshell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rou39.pocketshell.adb.AdbDiscovery
import com.rou39.pocketshell.adb.AdbShell
import com.rou39.pocketshell.data.Recipe
import com.rou39.pocketshell.ui.ThemePalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 実行の記録1件。error があれば実行できなかった（つなげなかった等）。 */
data class RunLog(
    val id: Long,
    val title: String,
    val command: String,
    val startedAt: Long,
    val running: Boolean = true,
    val output: String = "",
    val exitCode: Int? = null,
    val error: String? = null,
)

class ShellViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as PocketShellApp).recipes
    private val settings = (app as PocketShellApp).settings

    val palette: StateFlow<ThemePalette> = settings.palette.stateIn(viewModelScope, SharingStarted.Eagerly, ThemePalette.TERMINAL)

    fun setPalette(palette: ThemePalette) = viewModelScope.launch { settings.setPalette(palette) }
    private val discovery = AdbDiscovery(app, AdbDiscovery.Kind.CONNECT)

    val recipes: StateFlow<List<Recipe>> = store.recipes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** ワイヤレスデバッグの接続用ポート。見つかっていなければ null。 */
    val connectPort: StateFlow<Int?> = discovery.port

    /** 手で入れたポート。見つからないときの予備。 */
    val manualPort = MutableStateFlow<Int?>(null)

    private val _logs = MutableStateFlow<List<RunLog>>(emptyList())

    /** 実行の記録。新しいものが先頭。アプリを閉じると消える。 */
    val logs: StateFlow<List<RunLog>> = _logs.asStateFlow()
    private var nextId = 0L

    /** 画面が見えている間だけポートを探す。 */
    fun startDiscovery() = discovery.start()

    fun stopDiscovery() = discovery.stop()

    fun run(title: String, command: String) {
        if (command.isBlank()) return
        val id = nextId++
        val log = RunLog(id, title, command, System.currentTimeMillis())
        _logs.update { (listOf(log) + it).take(MAX_LOGS) }
        val port = connectPort.value ?: manualPort.value
        viewModelScope.launch {
            val done = if (port == null) {
                log.copy(running = false, error = "ワイヤレスデバッグの接続先が見つかりません。オンになっているか確認してください")
            } else {
                runCatching { AdbShell.run(port, command) }.fold(
                    onSuccess = { r ->
                        val text = listOf(r.output, r.errorOutput).filter { it.isNotEmpty() }.joinToString("\n")
                        log.copy(running = false, output = text, exitCode = r.exitCode)
                    },
                    onFailure = { e -> log.copy(running = false, error = AdbShell.describe(e)) },
                )
            }
            _logs.update { list -> list.map { if (it.id == id) done else it } }
        }
    }

    fun clearLogs() {
        _logs.value = _logs.value.filter { it.running }
    }

    fun save(recipe: Recipe) = viewModelScope.launch { store.save(recipe) }

    fun delete(id: String) = viewModelScope.launch { store.delete(id) }

    fun resetRecipes() = viewModelScope.launch { store.resetToExamples() }

    fun clearRecipes() = viewModelScope.launch { store.clear() }

    fun move(id: String, offset: Int) = viewModelScope.launch { store.move(id, offset) }

    override fun onCleared() = discovery.stop()

    private companion object {
        const val MAX_LOGS = 30
    }
}
