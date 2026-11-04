package com.rou39.pocketshell.adb

import android.content.Context
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.cert.OkioFilePrivateKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.net.ConnectException
import javax.net.ssl.SSLException

/** コマンドの結果。 */
data class ShellResult(val output: String, val errorOutput: String, val exitCode: Int)

/**
 * 端末自身の adbd（ワイヤレスデバッグ）へつなぎ、シェル権限でコマンドを実行する。
 * つなぐ先は 127.0.0.1 だけ。外部へは通信しない。
 */
object AdbShell {
    private const val LOCALHOST = "127.0.0.1"

    /** ペアリングで端末に登録する、このアプリの鍵の保存先（アプリの内部領域）。 */
    private const val KEY_FILE = "adb/adbkey.pem"

    /** アプリの起動時に一度だけ呼ぶ。鍵が無ければ初回の接続・ペアリングのときに作られる。 */
    fun init(context: Context) {
        val file = File(context.filesDir, KEY_FILE).apply { parentFile?.mkdirs() }
        KadbCert.configure(OkioFilePrivateKeyStore(file.toOkioPath(), FileSystem.SYSTEM))
    }

    /** ペア設定コードでペアリングする。port は「ペア設定コード」の画面に出ているポート。 */
    suspend fun pair(port: Int, code: String) {
        Kadb.pair(LOCALHOST, port, code.trim())
    }

    /** コマンドを実行して、終わるまで待つ。port はワイヤレスデバッグの接続用ポート。 */
    suspend fun run(port: Int, command: String): ShellResult = withContext(Dispatchers.IO) {
        Kadb.create(LOCALHOST, port).use { adb ->
            val r = adb.shell(command)
            ShellResult(r.output, r.errorOutput, r.exitCode)
        }
    }

    /** 失敗の理由を、次に何をすればよいか分かる言葉にする。 */
    fun describe(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }.toList()
        return when {
            chain.any { it is ConnectException } ->
                "ワイヤレスデバッグに接続できません。オンになっているか確認してください"
            chain.any { it is SSLException } ->
                "接続を断られました。まだペアリングしていないか、ペアリングが取り消されています"
            else -> error.message ?: error::class.java.simpleName
        }
    }
}
