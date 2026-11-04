package com.rou39.pocketshell.adb

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * ワイヤレスデバッグのオンオフ。設定値 adb_wifi_enabled を書き換える。
 * 書き換えには WRITE_SECURE_SETTINGS が要る。これは adb からしか付けられないので、
 * 一度ワイヤレスデバッグでつないだときに、Pocket Shell 自身の adb で [grantCommand] を実行して付ける。
 * 付けた権限は再起動しても残る。
 */
object WirelessDebugging {
    private const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"
    private const val SETTING = "adb_wifi_enabled"

    fun canToggle(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** オンオフを書き換える。権限が無ければ false。 */
    fun setEnabled(context: Context, enabled: Boolean): Boolean = runCatching {
        Settings.Global.putInt(context.contentResolver, SETTING, if (enabled) 1 else 0)
    }.getOrDefault(false)

    /** 自分に権限を付けるコマンド。ワイヤレスデバッグでつながっているときに実行する。 */
    fun grantCommand(context: Context): String = "pm grant ${context.packageName} $PERMISSION"
}
