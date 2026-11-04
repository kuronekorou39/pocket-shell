package com.rou39.pocketshell.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors

/**
 * ワイヤレスデバッグが出しているポートを、mDNS（NsdManager）で探す。
 * 同じ Wi-Fi の他の端末も同じ種類のサービスを出すので、この端末自身のアドレスのものだけを使う。
 */
class AdbDiscovery(context: Context, private val kind: Kind) {
    enum class Kind(val serviceType: String) {
        /** 「ペア設定コードによるデバイスのペア設定」を開いている間だけ出る。 */
        PAIRING("_adb-tls-pairing._tcp"),

        /** ワイヤレスデバッグがオンの間ずっと出る。コマンドの実行はこちらへつなぐ。 */
        CONNECT("_adb-tls-connect._tcp"),
    }

    private val nsd = context.getSystemService(NsdManager::class.java)
    private val executor = Executors.newSingleThreadExecutor()
    private val _port = MutableStateFlow<Int?>(null)

    /** 見つかったポート。見つかっていなければ null。 */
    val port: StateFlow<Int?> = _port.asStateFlow()

    private var discovery: NsdManager.DiscoveryListener? = null
    private val infoCallbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()

    fun start() {
        if (discovery != null) return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "discovery failed: $serviceType $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(info: NsdServiceInfo) = watch(info)

            override fun onServiceLost(info: NsdServiceInfo) = unwatch(info.serviceName)
        }
        discovery = listener
        nsd.discoverServices(kind.serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
        synchronized(infoCallbacks) {
            infoCallbacks.values.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
            infoCallbacks.clear()
        }
        _port.value = null
    }

    /** 見つけたサービスのアドレスとポートを追う。ポートはワイヤレスデバッグを入れ直すたびに変わる。 */
    private fun watch(info: NsdServiceInfo) {
        val name = info.serviceName
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.w(TAG, "resolve failed: $name $errorCode")
            }

            override fun onServiceUpdated(resolved: NsdServiceInfo) {
                if (resolved.hostAddresses.any(::isThisDevice)) _port.value = resolved.port
            }

            override fun onServiceLost() {
                _port.value = null
            }

            override fun onServiceInfoCallbackUnregistered() = Unit
        }
        synchronized(infoCallbacks) {
            if (name in infoCallbacks) return
            infoCallbacks[name] = callback
        }
        runCatching { nsd.registerServiceInfoCallback(info, executor, callback) }
            .onFailure { Log.w(TAG, "register callback failed", it) }
    }

    private fun unwatch(name: String) {
        val callback = synchronized(infoCallbacks) { infoCallbacks.remove(name) } ?: return
        runCatching { nsd.unregisterServiceInfoCallback(callback) }
    }

    private fun isThisDevice(address: InetAddress): Boolean =
        address.isLoopbackAddress || localAddresses().any { it == address }

    private fun localAddresses(): List<InetAddress> =
        runCatching { NetworkInterface.getNetworkInterfaces().toList().flatMap { it.inetAddresses.toList() } }
            .getOrDefault(emptyList())

    private companion object {
        const val TAG = "AdbDiscovery"
    }
}
