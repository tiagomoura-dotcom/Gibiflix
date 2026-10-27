package app.gibiflix.share

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import app.gibiflix.R
import app.gibiflix.container
import app.gibiflix.core.sync.DeviceInfo
import app.gibiflix.work.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

/** Estado do envio (servidor ligado) para a tela Compartilhar. */
data class SendState(
    val running: Boolean = false,
    val code: String = "",
    val name: String = "",
    val address: String = "",
    val error: String? = null,
    val events: Int = 0,
)

object ShareHub {
    val send = MutableStateFlow(SendState())
}

/**
 * Serviço em primeiro plano: mantém o servidor e o anúncio NSD (_gibiflix._tcp) enquanto a tela de
 * envio estiver aberta.
 */
class ShareService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ShareServer? = null
    private var nsd: NsdManager? = null
    private var registration: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notifications.simple(this, Notifications.CHANNEL_SHARE, getString(R.string.share_sending), getString(R.string.share_sending_hint))
        startForeground(Notifications.ID_SHARE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (server == null) startServer()
        return START_NOT_STICKY
    }

    private fun startServer() {
        val c = container
        scope.launch {
            try {
                val name = c.settings.deviceName()
                val deviceId = c.settings.deviceId()
                val s = ShareServer(
                    sync = c.sync,
                    files = c.files,
                    info = { DeviceInfo(name, ShareServer.appVersion(), c.deviceProfile.profile.name, deviceId) },
                    onEvent = { ShareHub.send.value = ShareHub.send.value.copy(events = ShareHub.send.value.events + 1) },
                )
                server = s
                val port = s.start()
                register(name, port)
                ShareHub.send.value = SendState(true, s.code, name, "${localIp() ?: "?"}:$port")
            } catch (e: Exception) {
                ShareHub.send.value = SendState(error = e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private fun register(name: String, port: Int) {
        val wifi = applicationContext.getSystemService(WifiManager::class.java)
        multicastLock = wifi?.createMulticastLock("gibiflix")?.apply { setReferenceCounted(false); acquire() }
        val manager = getSystemService(NsdManager::class.java) ?: return
        nsd = manager
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = ShareServer.SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    override fun onDestroy() {
        registration?.let { l -> runCatching { nsd?.unregisterService(l) } }
        runCatching { multicastLock?.release() }
        server?.stop()
        server = null
        ShareHub.send.value = SendState()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, ShareService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ShareService::class.java))
        }

        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        }.getOrNull()
    }
}
