package dev.dybug.routeros.device

import com.intellij.openapi.project.Project
import com.intellij.util.net.JdkProxyProvider
import dev.dybug.routeros.RosBundle
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.http.HttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

object RouterConnections {
    /** A client for the named (or default) profile. Reads the password safe, so call off the EDT. */
    fun client(project: Project, profileName: String?): Pair<RouterProfile, RouterRestClient> {
        val profile = RosDeviceSettings.getInstance(project).profile(profileName)
            ?: throw RouterException(RosBundle.message("device.no.profile"))
        return profile to client(profile, RouterCredentials.password(project, profile))
    }

    fun client(profile: RouterProfile, password: String): RouterRestClient =
        RouterRestClient(profile, password, proxySelector(profile))

    /**
     * The proxy for a profile. Routers are usually on the local network, which a corporate or
     * filtering proxy cannot reach, so [ProxyMode.AUTO] connects to local addresses directly.
     */
    fun proxySelector(profile: RouterProfile): ProxySelector {
        val direct = when (profile.proxyMode) {
            ProxyMode.NONE -> true
            ProxyMode.IDE -> false
            ProxyMode.AUTO -> runCatching { isLocal(RouterRestClient.parseBase(profile.url).host) }.getOrDefault(false)
        }
        return if (direct) HttpClient.Builder.NO_PROXY else JdkProxyProvider.getInstance().proxySelector
    }

    /** Local network names and addresses; other host names are resolved (call off the EDT). */
    fun isLocal(host: String): Boolean {
        val name = host.removePrefix("[").removeSuffix("]").lowercase().trimEnd('.')
        if (name.isEmpty()) return false
        if (name == "localhost" || LOCAL_SUFFIXES.any { name.endsWith(it) }) return true
        val literal = name.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) || ':' in name
        if (!literal && '.' !in name) return true // single-label name: resolved by the local network
        val addresses = try {
            InetAddress.getAllByName(name).toList()
        } catch (e: Exception) {
            return false
        }
        return addresses.isNotEmpty() && addresses.all(::isLocalAddress)
    }

    private fun isLocalAddress(address: InetAddress): Boolean =
        address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress ||
            // IPv6 unique local fc00::/7 and IPv4 carrier-grade NAT 100.64.0.0/10
            (address is Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc) ||
            (address is Inet4Address && address.address[0].toInt() == 100 && (address.address[1].toInt() and 0xc0) == 64)

    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home.arpa", ".internal", ".localdomain")

    /** SHA-256 of the certificate the router presents, for pinning self-signed certificates. */
    fun fetchFingerprint(url: String): String {
        val uri = RouterRestClient.parseBase(url)
        if (uri.scheme != "https") throw RouterException(RosBundle.message("device.pin.https.only"))
        var leaf: X509Certificate? = null
        val capture = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                leaf = chain?.firstOrNull()
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(capture), null) }
        (context.socketFactory.createSocket() as SSLSocket).use { socket ->
            socket.soTimeout = 10_000
            socket.connect(InetSocketAddress(uri.host, if (uri.port > 0) uri.port else 443), 10_000)
            socket.startHandshake()
        }
        return leaf?.let(RouterRestClient::sha256) ?: throw RouterException(RosBundle.message("device.pin.no.certificate"))
    }
}
