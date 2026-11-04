package com.rou39.pocketshell.adb

import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings

/** 画面のスイッチで切り替える、開発者向けオプションの項目。label は「デバッグ」を2行目に置く（狭い幅で途中で折れないように）。 */
enum class DebugSwitch(val label: String, val setting: String) {
    USB("USB\nデバッグ", "adb_enabled"),
    WIRELESS("ワイヤレス\nデバッグ", "adb_wifi_enabled"),
}

/**
 * USB デバッグ・ワイヤレスデバッグのオンオフ。設定値を直接書き換えるので、adb の接続が無くても切り替えられる。
 * 書き換えには WRITE_SECURE_SETTINGS が要る。これは adb からしか付けられないので、
 * 一度ワイヤレスデバッグでつないだときに、Pocket Shell 自身の adb で [grantCommand] を実行して付ける。
 * 付けた権限は再起動しても残る。今の値を読むだけなら権限は要らない。
 */
object DebugSettings {
    private const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    fun canToggle(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * 今オンか。USB デバッグの設定値は、アプリから読むと常に 0 が返る（Android 17 で確認）。
     * そこで USB の状態の通知（[USB_STATE]、最後の値が残る）にある adb の有無を使う。
     */
    fun isOn(context: Context, switch: DebugSwitch): Boolean = when (switch) {
        DebugSwitch.USB -> context.registerReceiver(null, IntentFilter(USB_STATE), Context.RECEIVER_EXPORTED)
            ?.getBooleanExtra(USB_EXTRA_ADB, false)
            ?: settingOn(context, switch)
        DebugSwitch.WIRELESS -> settingOn(context, switch)
    }

    private fun settingOn(context: Context, switch: DebugSwitch): Boolean =
        Settings.Global.getInt(context.contentResolver, switch.setting, 0) == 1

    /** USB の状態の通知（UsbManager.ACTION_USB_STATE。公開されていない名前なので文字で持つ）。 */
    const val USB_STATE = "android.hardware.usb.action.USB_STATE"
    private const val USB_EXTRA_ADB = "adb"

    /** オンオフを書き換える。権限が無ければ false。 */
    fun set(context: Context, switch: DebugSwitch, on: Boolean): Boolean = runCatching {
        Settings.Global.putInt(context.contentResolver, switch.setting, if (on) 1 else 0)
    }.getOrDefault(false)

    /** 値が変わったことを受け取るための場所（ContentObserver に渡す）。 */
    fun uri(switch: DebugSwitch): Uri = Settings.Global.getUriFor(switch.setting)

    /** 自分に権限を付けるコマンド。ワイヤレスデバッグでつながっているときに実行する。 */
    fun grantCommand(context: Context): String = "pm grant ${context.packageName} $PERMISSION"
}
