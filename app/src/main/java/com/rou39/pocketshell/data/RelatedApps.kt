package com.rou39.pocketshell.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * 一緒に使うと便利なアプリと、そのためのコマンド。入っているときだけ画面に出す。
 * 入っているかを調べる相手は AndroidManifest.xml の <queries> にも書く（書かないと見えない）。
 */
data class RelatedAction(val label: String, val command: String)

data class RelatedApp(val name: String, val packageName: String, val actions: List<RelatedAction>)

object RelatedApps {
    /** Bento Home のシステムモニター用の計測プロセスを起動する（Bento の docs/operations.md と同じコマンド）。 */
    private fun bentoMonitor(pkg: String) =
        "p=\$(pm path $pkg | head -n1 | cut -d: -f2); " +
            "CLASSPATH=\$p setsid app_process /system/bin com.rou39.bentohome.monitor.priv.Starter $pkg > /dev/null 2>&1 &"

    private fun bento(name: String, pkg: String) = RelatedApp(
        name = name,
        packageName = pkg,
        actions = listOf(RelatedAction("計測用プロセスを起動", bentoMonitor(pkg))),
    )

    private val ALL = listOf(
        bento("Bento Home", "com.rou39.bentohome"),
        bento("Bento Home（デバッグ版）", "com.rou39.bentohome.debug"),
    )

    /**
     * 入っている関連アプリ。キャプチャを撮るときは出さない（紛らわしいので）。
     * デバッグ版だけ、`adb shell setprop debug.pocketshell.screenshot 1` で隠せる。
     */
    fun installed(context: Context): List<RelatedApp> = if (screenshotMode(context)) emptyList() else ALL.filter { app ->
        runCatching { context.packageManager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0)) }.isSuccess
    }

    private fun screenshotMode(context: Context): Boolean {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return false
        return runCatching {
            Runtime.getRuntime().exec(arrayOf("getprop", "debug.pocketshell.screenshot")).inputStream.bufferedReader().readText().trim() == "1"
        }.getOrDefault(false)
    }
}
