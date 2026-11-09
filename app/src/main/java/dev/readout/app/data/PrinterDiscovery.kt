package dev.readout.app.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/**
 * Finds OctoPrint instances advertised over mDNS (`_octoprint._tcp`).
 *
 * Resolution is serialised because [NsdManager] rejects overlapping resolves; a second
 * discovery while one is running is ignored rather than queued.
 */
class PrinterDiscovery(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.DiscoveryListener? = null
    private val resolving = HashSet<String>()

    fun start(
        onFound: (DiscoveredPrinter) -> Unit,
        onFinished: (String?) -> Unit
    ) {
        stop()
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceType.contains("octoprint", ignoreCase = true)) return
                if (!resolving.add(info.serviceName)) return
                runCatching {
                    nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            resolving.remove(serviceInfo.serviceName)
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            resolving.remove(serviceInfo.serviceName)
                            val host = serviceInfo.host?.hostAddress?.trimEnd('.') ?: return
                            val port = serviceInfo.port
                            val label = serviceInfo.serviceName
                                .substringBefore("._octoprint")
                                .ifBlank { host }
                            onFound(DiscoveredPrinter(name = label, host = host, port = port))
                        }
                    })
                }.onFailure { resolving.remove(info.serviceName) }
            }

            override fun onServiceLost(info: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                onFinished("Discovery is unavailable on this network ($errorCode).")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        listener = discoveryListener
        runCatching {
            nsdManager.discoverServices("_octoprint._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure {
            listener = null
            onFinished("Discovery is unavailable on this network.")
        }
    }

    fun stop() {
        val current = listener ?: return
        listener = null
        runCatching { nsdManager.stopServiceDiscovery(current) }
        resolving.clear()
    }
}
