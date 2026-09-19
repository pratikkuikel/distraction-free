package com.distractionfree.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Full-capture local VPN with DNS filtering at its core. All IPv4 traffic
 * for captured apps is routed into the tun (Android gives a captured app no
 * fallback to the real network for anything the VPN doesn't route, so a
 * DNS-only route would silently kill all other connectivity — see
 * docs/troubleshooting.md). DNS queries (UDP/53) get the block/allow/
 * SafeSearch logic; everything else is relayed transparently via TcpNat/
 * UdpNat so the device keeps working normally. Every app's DNS is filtered
 * here except the ones in AppExemptions, which are excluded from the
 * tunnel entirely at the OS level.
 */
class BlockerVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.distractionfree.app.START"
        const val ACTION_STOP = "com.distractionfree.app.STOP" // logs a disable attempt; never actually stops
        const val ACTION_REFRESH_NOTIFICATION = "com.distractionfree.app.REFRESH_NOTIFICATION"
        private const val NOTIFICATION_CHANNEL_ID = "distraction-free-status"
        private const val NOTIFICATION_ID = 1
        private const val VPN_ADDRESS = "10.111.222.1"
        private const val VPN_DNS_ADDRESS = "10.111.222.2" // distinct from VPN_ADDRESS — some resolvers (Chrome) treat a DNS server matching the interface's own address as a suspicious/invalid config
        private const val FALLBACK_UPSTREAM_DNS = "1.1.1.1"

        @Volatile var isRunning = false
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private lateinit var alwaysOnTrie: DomainTrie
    private lateinit var adultTrie: DomainTrie
    private lateinit var socialTrie: DomainTrie
    private lateinit var youtubeTrie: DomainTrie
    private lateinit var schedule: ScheduleManager
    private lateinit var stats: Stats
    private lateinit var streak: StreakManager
    private lateinit var timeBack: TimeBack
    private lateinit var diagnosticLog: DiagnosticLog
    private var isOnline = true
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var tcpNat: TcpNat? = null
    private var udpNat: UdpNat? = null

    /** Schedule-gated IP backstop for TcpNat/UdpNat — see YoutubeIpBlocklist. */
    fun isYoutubeCdnBlockedNow(addr: ByteArray): Boolean =
        schedule.isSocialBlockedNow() && YoutubeIpBlocklist.isBlocked(addr)

    /** Schedule-gated IP backstop for TcpNat/UdpNat — see MetaCdnIpBlocklist. */
    fun isMetaCdnBlockedNow(addr: ByteArray): Boolean =
        schedule.isSocialBlockedNow() && MetaCdnIpBlocklist.isBlocked(addr)

    /**
     * Lets TcpNat/UdpNat record their IP-level blocks (DnsBypassBlocklist,
     * YoutubeIpBlocklist) into the same diagnostic log as DNS decisions —
     * those blocks happen below the DNS layer, so without this they'd be
     * invisible even while working correctly.
     */
    fun logDiagnostic(line: String) = diagnosticLog.log(line)
    @Volatile private var upstreamDnsServers: List<InetAddress> = emptyList()
    @Volatile private var underlyingNetwork: Network? = null

    override fun onCreate() {
        super.onCreate()
        schedule = ScheduleManager(this)
        stats = Stats(this)
        streak = StreakManager(this)
        timeBack = TimeBack(this)
        diagnosticLog = DiagnosticLog(this)
        alwaysOnTrie = BlocklistRepository.buildAlwaysOnTrie(this)
        adultTrie = BlocklistRepository.buildAdultTrie(this)
        socialTrie = BlocklistRepository.buildSocialTrie(this)
        youtubeTrie = BlocklistRepository.buildYoutubeTrie()
        YoutubeIpBlocklist.load(this)
        MetaCdnIpBlocklist.load(this)
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // The "disable" flow, exactly as designed: log it, never act on it.
                DisableLog.recordAttempt(this)
                return START_STICKY
            }
            ACTION_REFRESH_NOTIFICATION -> {
                if (running.get()) {
                    val nm = getSystemService(NotificationManager::class.java)
                    nm.notify(NOTIFICATION_ID, buildNotification())
                } else {
                    startVpn()
                }
                return START_STICKY
            }
            else -> startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        if (running.get()) return
        startForeground(NOTIFICATION_ID, buildNotification())
        captureUpstreamDnsServers()

        val builder = Builder()
            .addAddress(VPN_ADDRESS, 24)
            .addRoute("0.0.0.0", 0) // full capture — required so TcpNat/UdpNat can relay everything else
            .addDnsServer(VPN_DNS_ADDRESS)
            .setMtu(1400)
            .setSession("Distraction Free")
            .setBlocking(true)
        AppExemptions.applyTo(builder, packageManager)
        underlyingNetwork?.let { builder.setUnderlyingNetworks(arrayOf(it)) }

        vpnInterface = builder.establish() ?: return
        running.set(true)
        isRunning = true
        AlarmScheduler.scheduleNextTransitions(this)
        BlocklistUpdateScheduler.scheduleNext(this)
        ServiceWatchdog.scheduleHeartbeat(this)

        thread(name = "df-vpn-loop") { runPacketLoop() }
    }

    private fun runPacketLoop() {
        val iface = vpnInterface ?: return
        val input = FileInputStream(iface.fileDescriptor)
        val output = FileOutputStream(iface.fileDescriptor)
        tcpNat = TcpNat(this, output)
        udpNat = UdpNat(this, output)
        val buffer = ByteArray(32767)

        Log.i("df-vpn", "packet loop started")
        while (running.get()) {
            val length = try { input.read(buffer) } catch (e: Exception) {
                Log.e("df-vpn", "tun read failed: $e"); break
            }
            if (length <= 0) continue

            val ip = Ipv4Packet.parse(buffer, length) ?: continue // IPv6/unparseable — not handled (see docs)

            when (ip.protocol) {
                Ipv4Packet.PROTO_UDP -> {
                    val udp = Ipv4UdpPacket.parse(buffer, length) ?: continue
                    if (udp.dstPort == 53) {
                        stats.recordTotal()
                        handleDnsQuery(udp, output)
                    } else {
                        udpNat?.handle(udp)
                    }
                }
                Ipv4Packet.PROTO_TCP -> {
                    val tcp = TcpSegment.parse(ip, buffer, length) ?: continue
                    tcpNat?.handle(tcp)
                }
                else -> { /* ICMP etc — not relayed in this MVP */ }
            }
        }
    }

    private fun handleDnsQuery(packet: Ipv4UdpPacket, output: FileOutputStream) {
        val query = packet.payload
        val name = DnsMessage.questionName(query) ?: return
        val domain = name.lowercase()
        streak.touch() // cheap: no-op unless the calendar day has changed

        if (alwaysOnTrie.isBlocked(domain)) {
            stats.recordBlockedAlwaysOn()
            if (adultTrie.isBlocked(domain)) timeBack.recordAdultBlock() else timeBack.recordOtherBlock()
            diagnosticLog.log("BLOCK always-on $domain")
            writeResponse(packet, DnsMessage.nxdomainResponse(query), output)
            return
        }

        // Social-block check runs before SafeSearch: youtube.com is in both
        // lists (needs SafeSearch when open, hard-blocking when not), and
        // checking SafeSearch first meant the bare domain could never
        // actually be blocked during the nightly window — only its
        // subdomains (ytimg.com etc.) were. Blocked wins.
        if (schedule.isSocialBlockedNow() && socialTrie.isBlocked(domain)) {
            stats.recordBlockedSocial()
            if (youtubeTrie.isBlocked(domain)) timeBack.recordYoutubeBlock() else timeBack.recordSocialBlock()
            diagnosticLog.log("BLOCK social $domain")
            writeResponse(packet, DnsMessage.nxdomainResponse(query), output)
            return
        }

        SafeSearch.targets[domain]?.let { target ->
            stats.recordSafeSearchRewrite()
            val addr = SafeSearch.resolve(target)
            if (addr != null) {
                diagnosticLog.log("SAFESEARCH $domain -> $target")
                writeResponse(packet, DnsMessage.aRecordResponse(query, addr), output)
            } else {
                diagnosticLog.log("SAFESEARCH $domain resolve failed, forwarding normally")
                forward(packet, output) // fail open on resolve failure
            }
            return
        }

        diagnosticLog.log("ALLOW $domain")
        forward(packet, output)
    }

    private fun captureUpstreamDnsServers() {
        // Use the real (non-VPN) network's own configured DNS servers rather
        // than a hardcoded public resolver — respects captive portals/
        // corporate DNS, and some networks (incl. this emulator's) only
        // permit UDP/53 to their assigned resolvers, not arbitrary IPs.
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val active = cm.activeNetwork
            underlyingNetwork = active
            val servers = active?.let { cm.getLinkProperties(it)?.dnsServers } ?: emptyList()
            upstreamDnsServers = if (servers.isNotEmpty()) servers else listOf(InetAddress.getByName(FALLBACK_UPSTREAM_DNS))
            Log.i("df-vpn", "upstream DNS servers: ${upstreamDnsServers.map { it.hostAddress }}, underlying network: $active")
        } catch (e: Exception) {
            upstreamDnsServers = listOf(InetAddress.getByName(FALLBACK_UPSTREAM_DNS))
        }
    }

    /**
     * protect() alone should be enough to keep a relay socket outside our
     * own tunnel, but on-device testing (a ColorOS phone with WiFi and
     * cellular both active) showed widespread, otherwise-inexplicable
     * ECONNREFUSED on real, working destinations — consistent with the
     * socket ending up on an ambiguous or wrong underlying network. Binding
     * explicitly to the network captured at VPN start removes that
     * ambiguity. Falls back to protect()-only if binding fails, rather than
     * dropping the connection outright.
     */
    fun protectAndBind(socket: java.net.Socket) {
        protect(socket)
        underlyingNetwork?.let { net ->
            try { net.bindSocket(socket) } catch (e: Exception) {
                Log.e("df-vpn", "bindSocket (TCP) failed: $e")
            }
        }
    }

    fun protectAndBind(socket: DatagramSocket) {
        protect(socket)
        underlyingNetwork?.let { net ->
            try { net.bindSocket(socket) } catch (e: Exception) {
                Log.e("df-vpn", "bindSocket (UDP) failed: $e")
            }
        }
    }

    private fun forward(packet: Ipv4UdpPacket, output: FileOutputStream) {
        if (!isOnline) return // offline: drop immediately, no timeout wait, no retry churn, no battery burn

        for (upstreamAddr in upstreamDnsServers) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                protectAndBind(socket) // critical: keep this forwarding socket outside our own tunnel, on the right network
                socket.soTimeout = 2000

                socket.send(DatagramPacket(packet.payload, packet.payload.size, upstreamAddr, 53))

                val respBuf = ByteArray(512)
                val respPacket = DatagramPacket(respBuf, respBuf.size)
                socket.receive(respPacket)

                val responsePayload = respBuf.copyOfRange(0, respPacket.length)
                writeResponse(packet, responsePayload, output)
                return
            } catch (e: Exception) {
                Log.e("df-vpn", "forward to ${upstreamAddr.hostAddress} failed: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    private fun writeResponse(packet: Ipv4UdpPacket, payload: ByteArray, output: FileOutputStream) {
        try {
            output.write(packet.buildResponse(payload))
        } catch (e: Exception) {
            Log.e("df-vpn", "writeResponse failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline = true
                if (BlocklistUpdateScheduler.isRetryPending(this@BlockerVpnService)) retryBlocklistUpdateNow()
            }
            override fun onLost(network: Network) { isOnline = cm.activeNetwork != null }
        }
        cm.registerNetworkCallback(request, networkCallback!!)
        isOnline = cm.activeNetwork != null
    }

    /**
     * Failsafe path: a missed 4am update leaves a "retry pending" flag (see
     * BlocklistUpdateScheduler). Rather than wait for the 30min backstop
     * alarm, retry the moment this callback says connectivity is back.
     * Reloads the tries in place instead of a full VPN stop/start, so this
     * doesn't cause a connectivity blip for whatever's browsing right now.
     */
    private fun retryBlocklistUpdateNow() {
        thread(name = "df-blocklist-retry") {
            if (BlocklistUpdater.update(this)) {
                Log.i("df-vpn", "blocklist retry succeeded, reloading in place")
                alwaysOnTrie = BlocklistRepository.buildAlwaysOnTrie(this)
                adultTrie = BlocklistRepository.buildAdultTrie(this)
                socialTrie = BlocklistRepository.buildSocialTrie(this)
                YoutubeIpBlocklist.load(this)
                MetaCdnIpBlocklist.load(this)
                BlocklistUpdateScheduler.markUpdateSucceeded(this)
                BlocklistUpdateScheduler.scheduleNext(this)
            } else {
                Log.w("df-vpn", "blocklist retry failed, will retry again on next connectivity change or backstop alarm")
            }
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "Protection status", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Distraction Free is active")
            .setContentText("Filtering is on.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Many OEM skins kill the whole process when the app is swiped out of
        // recents. Ask the watchdog to check back in moments from now — an
        // alarm outlives the process, so if we get killed it restarts us; if
        // we're still running it's a no-op that just re-arms the heartbeat.
        ServiceWatchdog.scheduleHeartbeat(this, ServiceWatchdog.QUICK_CHECK_MS)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running.set(false)
        isRunning = false
        networkCallback?.let {
            try { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } catch (e: Exception) {}
        }
        try { vpnInterface?.close() } catch (e: Exception) {}
        super.onDestroy()
    }

    override fun onRevoke() {
        // The user revoked VPN permission from Android system settings — an
        // OS-level action outside this app's UI, same acknowledged limit as
        // "Force stop." We can't prevent it, only log that it happened.
        DisableLog.recordAttempt(this)
        super.onRevoke()
    }
}
