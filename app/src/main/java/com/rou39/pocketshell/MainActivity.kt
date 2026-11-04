package com.rou39.pocketshell

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rou39.pocketshell.adb.DebugSettings
import com.rou39.pocketshell.adb.DebugSwitch
import android.database.ContentObserver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import com.rou39.pocketshell.data.Recipe
import com.rou39.pocketshell.data.RelatedApp
import com.rou39.pocketshell.data.RelatedApps
import com.rou39.pocketshell.pairing.PairingService
import com.rou39.pocketshell.pairing.PairingState
import com.rou39.pocketshell.ui.PocketTheme
import com.rou39.pocketshell.ui.ThemePalette
import com.rou39.pocketshell.ui.colorSchemeFor
import com.rou39.pocketshell.ui.isLight
import com.rou39.pocketshell.ui.ng
import com.rou39.pocketshell.ui.ok
import com.rou39.pocketshell.ui.output
import android.graphics.Color as AndroidColor
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.semantics.Role
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MONO = FontFamily.Monospace

/** Android 17 のローカルネットワークの許可。 */
private const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/** レシピの整理の確認で、どちらの操作か。 */
private enum class RecipeReset { EXAMPLES, CLEAR }

private const val TAB_RUN = 0
private const val TAB_LOG = 1

/**
 * 1画面のアプリ。上に「実行」と「記録」のタブ。
 * 実行のタブに接続の状態・関連アプリ・レシピ・その場で実行を並べ、実行したら記録のタブへ移って結果を見せる。
 */
class MainActivity : ComponentActivity() {
    private val vm: ShellViewModel by viewModels()

    /** ローカルネットワークの許可があるか。無いとワイヤレスデバッグのポートを探せない。 */
    private var localNetworkGranted by mutableStateOf(false)

    /** 入っている関連アプリ。画面に戻るたびに調べ直す。 */
    private var relatedApps by mutableStateOf<List<RelatedApp>>(emptyList())

    private val pairingPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPermissions()
        // 通知を断られても待ち受けは始める（そのときは画面のポートの手入力を使う）
        if (localNetworkGranted) startPairing()
    }

    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshPermissions()
        if (localNetworkGranted) vm.startDiscovery()
    }

    private fun refreshPermissions() {
        localNetworkGranted = checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
    }

    /** USB デバッグ・ワイヤレスデバッグの今の値。 */
    private var switchStates by mutableStateOf<Map<DebugSwitch, Boolean>>(emptyMap())

    private fun refreshSwitches() {
        switchStates = DebugSwitch.entries.associateWith { DebugSettings.isOn(this, it) }
    }

    private val switchObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshSwitches()
    }

    /** USB デバッグは設定値では追えないので、USB の状態の通知で追う。 */
    private val usbStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshSwitches()
    }

    override fun onStart() {
        super.onStart()
        DebugSwitch.entries.forEach { contentResolver.registerContentObserver(DebugSettings.uri(it), false, switchObserver) }
        registerReceiver(usbStateReceiver, IntentFilter(DebugSettings.USB_STATE), RECEIVER_EXPORTED)
        refreshSwitches()
    }

    override fun onStop() {
        contentResolver.unregisterContentObserver(switchObserver)
        unregisterReceiver(usbStateReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        relatedApps = RelatedApps.installed(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val palette by vm.palette.collectAsState()
            val light = colorSchemeFor(palette, this).isLight()
            LaunchedEffect(light) {
                val style = if (light) {
                    SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                } else {
                    SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            PocketTheme(palette) {
                Surface(Modifier.fillMaxSize()) { Screen() }
            }
        }
    }

    private fun requestPairing() {
        pairingPermissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS, LOCAL_NETWORK))
    }

    private fun startPairing() {
        PairingService.start(this)
        openDeveloperOptions()
    }

    private fun openDeveloperOptions() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
    }

    private fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("output", text))
    }

    @Composable
    private fun Screen() {
        val logs by vm.logs.collectAsState()
        val palette by vm.palette.collectAsState()
        var tab by rememberSaveable { mutableIntStateOf(TAB_RUN) }
        var choosingPalette by rememberSaveable { mutableStateOf(false) }

        // 画面が見えている間だけ接続先を探す（許可が無いと探すたびに選択画面が出るので、許可があるときだけ）
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> if (checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) {
                        vm.startDiscovery()
                    }
                    Lifecycle.Event.ON_STOP -> vm.stopDiscovery()
                    else -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }

        // 実行したら記録のタブへ移り、結果が見えるようにする
        val run: (String, String) -> Unit = { title, command ->
            vm.run(title, command)
            tab = TAB_LOG
        }

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
            ) {
                Text(
                    ">_",
                    fontFamily = MONO,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.width(10.dp))
                Text("Pocket Shell", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { choosingPalette = true }) { Text("◐ 色") }
            }
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                Tab(selected = tab == TAB_RUN, onClick = { tab = TAB_RUN }, text = { Text("実行") })
                Tab(
                    selected = tab == TAB_LOG,
                    onClick = { tab = TAB_LOG },
                    text = { Text(if (logs.isEmpty()) "記録" else "記録 ${logs.size}") },
                )
            }
            Box(Modifier.weight(1f).imePadding()) {
                when (tab) {
                    TAB_RUN -> RunTab(run)
                    else -> LogTab(logs)
                }
            }
        }
        if (choosingPalette) {
            PaletteDialog(palette, onSelect = vm::setPalette, onDismiss = { choosingPalette = false })
        }
    }

    /** 配色を選ぶ。押すとすぐ切り替わるので、見ながら選べる。 */
    @Composable
    private fun PaletteDialog(selected: ThemePalette, onSelect: (ThemePalette) -> Unit, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("色") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (row in ThemePalette.entries.chunked(3)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            for (p in row) {
                                val scheme = colorSchemeFor(p, this@MainActivity)
                                val chosen = p == selected
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .selectable(selected = chosen, role = Role.RadioButton, onClick = { onSelect(p) })
                                        .padding(vertical = 8.dp),
                                ) {
                                    // 地の色の上に、文字色で「>_」を描いた見本
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .border(
                                                if (chosen) 3.dp else 1.dp,
                                                if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(14.dp),
                                            )
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(scheme.surfaceContainer),
                                    ) {
                                        Text(">_", fontFamily = MONO, fontWeight = FontWeight.Bold, color = scheme.primary)
                                    }
                                    Text(p.label, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
        )
    }

    @Composable
    private fun RunTab(run: (String, String) -> Unit) {
        val recipes by vm.recipes.collectAsState()
        val port by vm.connectPort.collectAsState()
        val manualPort by vm.manualPort.collectAsState()
        val pairing by PairingService.state.collectAsState()
        var editing by remember { mutableStateOf<Recipe?>(null) }
        var input by rememberSaveable { mutableStateOf("") }
        var recipeMenu by remember { mutableStateOf(false) }
        var confirm by remember { mutableStateOf<RecipeReset?>(null) }
        // 権限を付けるコマンドを実行したあとに分かるよう、記録が変わるたびに調べ直す
        val logs by vm.logs.collectAsState()
        val canToggle = remember(logs, port) { DebugSettings.canToggle(this@MainActivity) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { SwitchRow(canToggle, connected = port != null, run) }
            item { ConnectionCard(port, manualPort, pairing, canToggle) }
            if (relatedApps.isNotEmpty()) {
                item { SectionTitle("関連アプリ") }
                items(relatedApps, key = { it.packageName }) { app -> RelatedAppCard(app, run) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("レシピ", Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { recipeMenu = true }) { Text("整理") }
                        DropdownMenu(expanded = recipeMenu, onDismissRequest = { recipeMenu = false }) {
                            DropdownMenuItem(text = { Text("最初の例に戻す") }, onClick = {
                                recipeMenu = false
                                confirm = RecipeReset.EXAMPLES
                            })
                            DropdownMenuItem(text = { Text("すべて削除") }, onClick = {
                                recipeMenu = false
                                confirm = RecipeReset.CLEAR
                            })
                        }
                    }
                }
            }
            if (recipes.isEmpty()) {
                item { Note("レシピはありません。「＋ レシピを追加」か、その場で打ったコマンドの「レシピにする」で作れます。") }
            }
            items(recipes, key = { it.id }) { recipe ->
                RecipeCard(
                    recipe,
                    first = recipe == recipes.first(),
                    last = recipe == recipes.last(),
                    onRun = { run(recipe.name, recipe.command) },
                    onEdit = { editing = recipe },
                    onMove = { vm.move(recipe.id, it) },
                )
            }
            item {
                OutlinedButton(onClick = { editing = Recipe(name = "", command = "") }, modifier = Modifier.fillMaxWidth()) {
                    Text("＋ レシピを追加")
                }
            }
            item { SectionTitle("その場で実行") }
            item {
                Card {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("例: pm list packages -3", fontFamily = MONO) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MONO),
                        minLines = 4,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { input = "" }, enabled = input.isNotEmpty()) { Text("消す") }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { editing = Recipe(name = "", command = input.trim()) },
                            enabled = input.isNotBlank(),
                        ) { Text("レシピにする") }
                        Button(onClick = { run("その場で実行", input) }, enabled = input.isNotBlank()) { Text("▶ 実行") }
                    }
                }
            }
        }

        confirm?.let { kind ->
            AlertDialog(
                onDismissRequest = { confirm = null },
                title = { Text(if (kind == RecipeReset.CLEAR) "すべてのレシピを削除しますか" else "最初の例に戻しますか") },
                text = {
                    Text(
                        if (kind == RecipeReset.CLEAR) {
                            "登録したレシピをすべて消します。元に戻せません。"
                        } else {
                            "登録したレシピを消して、最初から入っている例だけにします。元に戻せません。"
                        },
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (kind == RecipeReset.CLEAR) vm.clearRecipes() else vm.resetRecipes()
                        confirm = null
                    }) { Text(if (kind == RecipeReset.CLEAR) "削除" else "戻す", color = MaterialTheme.colorScheme.ng) }
                },
                dismissButton = { TextButton(onClick = { confirm = null }) { Text("やめる") } },
            )
        }

        editing?.let { recipe ->
            RecipeEditor(
                recipe,
                isNew = recipes.none { it.id == recipe.id },
                onSave = {
                    vm.save(it)
                    editing = null
                },
                onDelete = {
                    vm.delete(recipe.id)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }

    @Composable
    private fun LogTab(logs: List<RunLog>) {
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "まだ実行していません。\n「実行」のタブでレシピやコマンドを実行すると、結果がここに出ます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Note("新しい順。アプリを閉じると消えます。", Modifier.weight(1f))
                    TextButton(onClick = vm::clearLogs) { Text("すべて消す") }
                }
            }
            items(logs, key = { it.id }) { log -> LogCard(log) }
        }
    }

    /**
     * USB デバッグ・ワイヤレスデバッグのスイッチ。横に並べる。
     * 切り替えの許可がまだ無ければスイッチは今の状態を見せるだけにし、つながっていれば許可を付けるボタンを出す。
     */
    @Composable
    private fun SwitchRow(canToggle: Boolean, connected: Boolean, run: (String, String) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (sw in DebugSwitch.entries) {
                    val on = switchStates[sw] == true
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            )
                            .toggleable(
                                value = on,
                                enabled = canToggle,
                                role = Role.Switch,
                                onValueChange = { v ->
                                    DebugSettings.set(this@MainActivity, sw, v)
                                    refreshSwitches()
                                },
                            )
                            .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    ) {
                        Text(
                            sw.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = on, onCheckedChange = null, enabled = canToggle)
                    }
                }
            }
            if (!canToggle) {
                if (connected) {
                    // つながっている今のうちに自分に権限を付けておくと、次からはスイッチで切り替えられる
                    Note("許可すると、このスイッチで切り替えられるようになります。再起動しても残ります。")
                    FilledTonalButton(onClick = { run("スイッチで切り替えられるようにする", DebugSettings.grantCommand(this@MainActivity)) }) {
                        Text("スイッチで切り替えられるようにする")
                    }
                } else {
                    Note("スイッチで切り替えるには、一度ワイヤレスデバッグでつないで許可を付けます（下の手順）。")
                }
            }
        }
    }

    /** 接続の状態。ワイヤレスデバッグの接続先が見つかっているか、ペアリングの進み具合。 */
    @Composable
    private fun ConnectionCard(
        port: Int?,
        manualPort: Int?,
        pairing: PairingState,
        canToggle: Boolean,
    ) {
        var showManual by rememberSaveable { mutableStateOf(false) }
        // つながっていてペアリングも済んでいれば、説明は畳んで1行にする
        val ready = port != null && localNetworkGranted
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (port != null) MaterialTheme.colorScheme.ok else MaterialTheme.colorScheme.ng),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (port != null) "ワイヤレスデバッグに接続できます（ポート $port）" else "ワイヤレスデバッグが見つかりません",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!localNetworkGranted) {
                Note("ワイヤレスデバッグのポートを探すのに「ローカルネットワーク」の許可が要ります（端末の中だけを探します）。")
                Button(onClick = { localNetworkPermission.launch(LOCAL_NETWORK) }) { Text("許可する") }
            } else if (port == null && canToggle) {
                Note("上のスイッチでワイヤレスデバッグをオンにしてください。Wi-Fi につながっている必要があります。初めてつなぐ Wi-Fi では、開発者向けオプションから一度オンにしてください。")
            } else if (port == null) {
                Note("開発者向けオプションで「ワイヤレスデバッグ」をオンにしてください。Wi-Fi につながっている必要があります。再起動するとオフに戻ります。")
            }
            pairingMessage(pairing)?.let { Note(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = ::openDeveloperOptions) { Text("開発者向けオプション") }
                FilledTonalButton(
                    onClick = ::requestPairing,
                    enabled = pairing !is PairingState.Searching && pairing !is PairingState.Ready && pairing !is PairingState.Pairing,
                ) { Text("ペアリング") }
            }
            if (!ready && !canToggle) {
                Note(
                    "ペアリングは最初の1回だけ。「ペアリング」を押すと設定が開くので、「ワイヤレスデバッグ」→" +
                        "「ペア設定コードによるデバイスのペア設定」を開きます。コードのダイアログは開いたまま、" +
                        "画面の上から通知欄を下ろし、Pocket Shell の通知の「コードを入力」に6桁を入れます。",
                )
            }
            TextButton(onClick = { showManual = !showManual }) { Text(if (showManual) "ポートの手入力を閉じる" else "ポートを手で入れる") }
            if (showManual) {
                OutlinedTextField(
                    value = manualPort?.toString().orEmpty(),
                    onValueChange = { v -> vm.manualPort.value = v.filter(Char::isDigit).take(5).toIntOrNull() },
                    label = { Text("接続用ポート（「ワイヤレスデバッグ」の画面の IP アドレスとポートの、コロンの後ろ）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    private fun pairingMessage(s: PairingState): String? = when (s) {
        PairingState.Idle -> null
        PairingState.Searching -> "ペアリング: 「ペア設定コードによるデバイスのペア設定」を開いてください"
        is PairingState.Ready -> "ペアリング: 通知にコードを入力してください"
        PairingState.Pairing -> "ペアリング: 確認しています…"
        PairingState.Done -> "ペアリング済み"
        is PairingState.Failed -> s.message
    }

    /** 入っている関連アプリの操作。中身のコマンドは見せず、ボタンだけにする。 */
    @Composable
    private fun RelatedAppCard(app: RelatedApp, run: (String, String) -> Unit) {
        Card {
            Text(app.name, style = MaterialTheme.typography.titleMedium)
            for (action in app.actions) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(action.label, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { run("${app.name}: ${action.label}", action.command) }) { Text("▶ 実行") }
                }
            }
        }
    }

    @Composable
    private fun RecipeCard(
        recipe: Recipe,
        first: Boolean,
        last: Boolean,
        onRun: () -> Unit,
        onEdit: () -> Unit,
        onMove: (Int) -> Unit,
    ) {
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(recipe.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        recipe.command,
                        fontFamily = MONO,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalButton(onClick = onRun, modifier = Modifier.padding(start = 8.dp)) { Text("▶ 実行") }
            }
            Row {
                TextButton(onClick = onEdit) { Text("✎ 編集") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onMove(-1) }, enabled = !first) { Text("↑") }
                TextButton(onClick = { onMove(1) }, enabled = !last) { Text("↓") }
            }
        }
    }

    @Composable
    private fun LogCard(log: RunLog) {
        val time = remember(log.startedAt) { SimpleDateFormat("HH:mm:ss", Locale.JAPAN).format(Date(log.startedAt)) }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(log.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "$time  $ ${log.command}",
                        fontFamily = MONO,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    log.running -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    log.error != null -> Text("失敗", color = MaterialTheme.colorScheme.ng)
                    else -> Text(
                        "終了 ${log.exitCode}",
                        color = if (log.exitCode == 0) MaterialTheme.colorScheme.ok else MaterialTheme.colorScheme.ng,
                    )
                }
            }
            val body = log.error ?: log.output
            if (!log.running && body.isNotEmpty()) {
                SelectionContainer {
                    Text(
                        body,
                        fontFamily = MONO,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.output)
                            .padding(10.dp),
                    )
                }
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { copy(body) }) { Text("コピー") }
                }
            } else if (!log.running) {
                Note("出力はありません")
            }
        }
    }

    /** レシピの追加・編集。コマンドが長くても見渡せるよう、画面の幅いっぱいに広げる。 */
    @Composable
    private fun RecipeEditor(
        recipe: Recipe,
        isNew: Boolean,
        onSave: (Recipe) -> Unit,
        onDelete: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        var name by remember(recipe.id) { mutableStateOf(recipe.name) }
        var command by remember(recipe.id) { mutableStateOf(recipe.command) }
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            title = { Text(if (isNew) "レシピを追加" else "レシピを編集") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("名前") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        command, { command = it },
                        label = { Text("コマンド") },
                        supportingText = { Text("adb shell と同じ権限で動きます") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = MONO),
                        minLines = 6,
                        maxLines = 14,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!isNew) TextButton(onClick = onDelete) { Text("このレシピを削除", color = MaterialTheme.colorScheme.ng) }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSave(recipe.copy(name = name.trim().ifEmpty { "名前なし" }, command = command.trim())) },
                    enabled = command.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("やめる") } },
        )
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp),
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}
