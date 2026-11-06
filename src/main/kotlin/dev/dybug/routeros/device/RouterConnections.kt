package dev.dybug.routeros.device

import com.intellij.openapi.project.Project
import com.intellij.util.net.JdkProxyProvider
import dev.dybug.routeros.RosBundle
import java.net.InetSocketAddress
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
        RouterRestClient(profile, password, JdkProxyProvider.getInstance().proxySelector)

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
