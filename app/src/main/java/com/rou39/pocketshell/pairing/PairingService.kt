package com.rou39.pocketshell.pairing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import com.rou39.pocketshell.MainActivity
import com.rou39.pocketshell.R
import com.rou39.pocketshell.adb.AdbDiscovery
import com.rou39.pocketshell.adb.AdbShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** ペアリングの進み具合。画面と通知で同じものを見せる。 */
sealed interface PairingState {
    data object Idle : PairingState

    /** 「専用コード（ペア設定コード）によるデバイスのペア設定」の画面が開くのを待っている（Android 17 は「専用コード」、それより前は「ペア設定コード」）。 */
    data object Searching : PairingState

    /** 画面が開いた。通知からコードを入力できる。 */
    data class Ready(val port: Int) : PairingState
    data object Pairing : PairingState
    data object Done : PairingState
    data class Failed(val message: String) : PairingState
}

/**
 * ペアリングを待つ。ペア設定コードは設定画面のダイアログを開いたままでないと有効にならないので、
 * このアプリを前面に出さず、通知の返信欄からコードを受け取る（Shizuku と同じ流れ）。
 * 設定画面を開いている間にこのアプリが止められないよう、フォアグラウンドサービスにする。
 */
class PairingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var discovery: AdbDiscovery? = null
    private var timeout: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CODE -> {
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)?.toString()
                val port = (state.value as? PairingState.Ready)?.port
                if (code.isNullOrBlank() || port == null) return START_NOT_STICKY
                pair(port, code)
            }
            ACTION_CANCEL -> finish(PairingState.Idle)
            else -> begin()
        }
        return START_NOT_STICKY
    }

    private fun begin() {
        createChannel()
        update(PairingState.Searching)
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        val d = discovery ?: AdbDiscovery(this, AdbDiscovery.Kind.PAIRING).also { discovery = it }
        d.start()
        scope.launch {
            d.port.collect { port ->
                val s = state.value
                if (port != null && (s is PairingState.Searching || s is PairingState.Ready)) {
                    update(PairingState.Ready(port))
                } else if (port == null && s is PairingState.Ready) {
                    // ダイアログを閉じた
                    update(PairingState.Searching)
                }
            }
        }
        timeout?.cancel()
        timeout = scope.launch {
            delay(TIMEOUT_MS)
            finish(PairingState.Failed("時間切れです。もう一度「ペアリング」から始めてください"))
        }
    }

    private fun pair(port: Int, code: String) {
        update(PairingState.Pairing)
        scope.launch {
            runCatching { AdbShell.pair(port, code) }
                .onSuccess { finish(PairingState.Done) }
                .onFailure { e ->
                    // コードの打ち間違いはよくあるので、待ち受けは続けてやり直せるようにする
                    update(
                        PairingState.Failed(
                            "ペアリングできませんでした: ${AdbShell.describe(e)}。" +
                                "すでにペアリング済みのときも失敗します（アプリに「接続できます」と出ていれば不要です）",
                        ),
                    )
                    delay(RETRY_DELAY_MS)
                    discovery?.port?.value?.let { update(PairingState.Ready(it)) } ?: update(PairingState.Searching)
                }
        }
    }

    /** 待ち受けをやめ、結果だけを通常の通知に残す。 */
    private fun finish(result: PairingState) {
        timeout?.cancel()
        discovery?.stop()
        _state.value = result
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (result !is PairingState.Idle) {
            getSystemService(NotificationManager::class.java).notify(RESULT_NOTIFICATION_ID, notification())
        }
        stopSelf()
    }

    override fun onDestroy() {
        discovery?.stop()
        scope.cancel()
        if (state.value !is PairingState.Done && state.value !is PairingState.Failed) _state.value = PairingState.Idle
    }

    private fun update(s: PairingState) {
        _state.value = s
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Pocket Shell のペアリング")
            .setOnlyAlertOnce(true)
        // 待っている間は通知の本体を押してもアプリを開かない。開くと設定のダイアログが閉じ、コードが無効になる
        if (state.value !is PairingState.Searching && state.value !is PairingState.Ready) builder.setContentIntent(open)
        when (val s = state.value) {
            PairingState.Searching -> builder
                .setContentText("「ワイヤレスデバッグ」→「専用コード（ペア設定コード）によるデバイスのペア設定」を開いてください")
                .setOngoing(true)
                .addAction(cancelAction())
            is PairingState.Ready -> {
                val text = "ダイアログは開いたまま、下の「コードを入力」を押して6桁を入れてください"
                builder
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setOngoing(true)
                    .addAction(codeAction())
                    .addAction(cancelAction())
            }
            PairingState.Pairing -> builder.setContentText("ペアリングしています…").setOngoing(true)
            PairingState.Done -> builder.setContentText("ペアリングしました。アプリに戻ってコマンドを実行できます").setAutoCancel(true)
            is PairingState.Failed -> builder.setContentText(s.message).setStyle(Notification.BigTextStyle().bigText(s.message))
            PairingState.Idle -> builder
        }
        return builder.build()
    }

    private fun codeAction(): Notification.Action {
        val input = RemoteInput.Builder(KEY_CODE).setLabel("ペア設定コード").build()
        // 返信欄の内容を書き込んで渡すので、変更可能な PendingIntent にする
        val intent = PendingIntent.getService(
            this, 1, Intent(this, PairingService::class.java).setAction(ACTION_CODE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return Notification.Action.Builder(null, "コードを入力", intent).addRemoteInput(input).build()
    }

    private fun cancelAction(): Notification.Action {
        val intent = PendingIntent.getService(
            this, 2, Intent(this, PairingService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(null, "やめる", intent).build()
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        // 設定画面の上に出して、そのまま入力できるよう重要度を高くする
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "ペアリング", NotificationManager.IMPORTANCE_HIGH),
        )
    }

    companion object {
        private const val CHANNEL_ID = "pairing"
        private const val NOTIFICATION_ID = 1
        private const val RESULT_NOTIFICATION_ID = 2
        private const val ACTION_CODE = "com.rou39.pocketshell.PAIRING_CODE"
        private const val ACTION_CANCEL = "com.rou39.pocketshell.PAIRING_CANCEL"
        private const val KEY_CODE = "code"

        /** 待ち受けの上限。ダイアログを開かないまま放置したときに止める。 */
        private const val TIMEOUT_MS = 5 * 60_000L

        /** 失敗を見せてから、入力をやり直せる状態に戻すまでの時間。 */
        private const val RETRY_DELAY_MS = 4_000L

        private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
        val state: StateFlow<PairingState> = _state.asStateFlow()

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PairingService::class.java))
        }

        /** 待ち受けをやめる（アプリの「ペアリングをやめる」から）。 */
        fun cancel(context: Context) {
            if (state.value is PairingState.Idle) return
            context.startService(Intent(context, PairingService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
