package app.gibiflix.share

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

data class FoundDevice(val name: String, val host: String, val port: Int)

/** Procura aparelhos Gibiflix na rede (NSD, _gibiflix._tcp). Resolve um de cada vez. */
object Discovery {
    @Suppress("DEPRECATION")
    fun devices(context: Context): Flow<List<FoundDevice>> = callbackFlow {
        val nsd = context.getSystemService(NsdManager::class.java)
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("gibiflix-discovery")?.apply { setReferenceCounted(false); acquire() }
        val found = LinkedHashMap<String, FoundDevice>()
        val pending = ConcurrentLinkedQueue<NsdServiceInfo>()
        val resolving = AtomicBoolean(false)

        fun resolveNext() {
            if (!resolving.compareAndSet(false, true)) return
            val info = pending.poll() ?: run { resolving.set(false); return }
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    resolving.set(false); resolveNext()
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress
                    if (host != null) {
                        synchronized(found) { found[serviceInfo.serviceName] = FoundDevice(serviceInfo.serviceName, host, serviceInfo.port) }
                        trySend(synchronized(found) { found.values.toList() })
                    }
                    resolving.set(false); resolveNext()
                }
            })
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                pending += serviceInfo
                resolveNext()
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(found) { found.remove(serviceInfo.serviceName) }
                trySend(synchronized(found) { found.values.toList() })
            }
        }
        trySend(emptyList())
        runCatching { nsd.discoverServices(ShareServer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            runCatching { lock?.release() }
        }
    }
}
