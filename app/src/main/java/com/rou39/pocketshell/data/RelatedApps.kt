package com.rou39.pocketshell.data

import android.content.Context
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

    fun installed(context: Context): List<RelatedApp> = ALL.filter { app ->
        runCatching { context.packageManager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0)) }.isSuccess
    }
}
