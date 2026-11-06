package dev.dybug.routeros.device

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import java.io.IOException
import java.net.ProxySelector
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Duration
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/** A failed router call, with a message safe to show to the user (never contains credentials). */
class RouterException(message: String, val status: Int = 0, cause: Throwable? = null) : IOException(message, cause)

/**
 * Minimal client for the RouterOS 7 REST API (`/rest/...`, HTTP Basic, JSON). Blocking: call it
 * from background threads only.
 */
class RouterRestClient(
    private val profile: RouterProfile,
    password: String,
    proxySelector: ProxySelector? = null,
) {
    private val base: URI = parseBase(profile.url).also {
        // VERIFY and PINNED promise protected credentials; plain HTTP needs the explicit INSECURE mode
        if (it.scheme == "http" && profile.tlsMode != TlsMode.INSECURE) {
            throw RouterException("Plain http:// sends the password unencrypted. Use https:// or set TLS mode to INSECURE.")
        }
    }
    private val authorization = "Basic " + Base64.getEncoder().encodeToString("${profile.user}:$password".toByteArray())
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .apply { if (proxySelector != null) proxy(proxySelector) }
        .apply { if (base.scheme == "https") sslContext(sslContext(profile)) }
        .build()

    /** `GET /rest/system/resource`: version, board name, uptime… */
    fun resource(): JsonObject = request("GET", "system/resource", null, Duration.ofSeconds(15)).asJsonObject

    fun version(): String = resource().get("version")?.asString.orEmpty()

    /** Runs a script and returns what it printed; runtime errors are appended as `… (line N column M)`. */
    fun execute(script: String, timeout: Duration = Duration.ofSeconds(profile.timeoutSeconds.toLong())): String {
        val body = JsonObject().apply {
            addProperty("script", script)
            addProperty("as-string", "yes")
        }
        val result = request("POST", "execute", body, timeout)
        return string(result, "ret")
    }

    /** Starts a script as a background job and returns at once (the job id). */
    fun executeInBackground(script: String): String {
        val body = JsonObject().apply { addProperty("script", script) }
        val result = request("POST", "execute", body, Duration.ofSeconds(30))
        return string(result, "ret")
    }

    /** Contents of a router file, or null if it does not exist. Large files are truncated by RouterOS. */
    fun fileContents(name: String): String? {
        val array = request("GET", "file?name=" + encode(name), null, Duration.ofSeconds(30)) as? JsonArray ?: return null
        return array.firstOrNull()?.asJsonObject?.get("contents")?.asString
    }

    /** One RouterOS highlight class per character of [input] (non-ASCII characters are sent as `?`). */
    fun highlight(input: String): List<String> {
        val body = JsonObject().apply {
            addProperty("request", "highlight")
            addProperty("input", input.map { if (it.code < 128) it else '?' }.joinToString(""))
        }
        // fail closed: validation must not pass because the answer had an unexpected shape
        val array = request("POST", "console/inspect", body, Duration.ofSeconds(60)) as? JsonArray ?: throw protocol()
        val highlight = guarded {
            array.map { it.asJsonObject }.firstOrNull { it.get("type")?.asString == "highlight" }?.get("highlight")?.asString
        } ?: throw protocol()
        return highlight.split(',')
    }

    /** Children of a menu path: `[{name, node-type}]` with node-type dir, cmd, arg or path. */
    fun inspectChildren(path: List<String>): List<Pair<String, String>> {
        val body = JsonObject().apply {
            addProperty("request", "child")
            addProperty("path", path.joinToString(","))
        }
        val array = request("POST", "console/inspect", body, Duration.ofSeconds(30)) as? JsonArray ?: return emptyList()
        return array.map { it.asJsonObject }
            .filter { it.get("type")?.asString == "child" }
            .mapNotNull { obj -> obj.get("name")?.asString?.let { it to (obj.get("node-type")?.asString ?: "") } }
    }

    fun putFile(name: String, contents: String) {
        val body = JsonObject().apply {
            addProperty("name", name)
            addProperty("contents", contents)
        }
        request("PUT", "file", body, Duration.ofSeconds(60))
    }

    fun deleteFile(name: String) {
        request("DELETE", "file/" + encode(name), null, Duration.ofSeconds(30))
    }

    fun putScript(name: String, source: String) {
        val body = JsonObject().apply {
            addProperty("name", name)
            addProperty("source", source)
        }
        request("PUT", "system/script", body, Duration.ofSeconds(60))
    }

    fun deleteScript(name: String) {
        request("DELETE", "system/script/" + encode(name), null, Duration.ofSeconds(30))
    }

    /** Running script jobs: `.id` and the script name (empty for /execute jobs). */
    fun jobs(): List<Pair<String, String>> {
        val array = request("GET", "system/script/job", null, Duration.ofSeconds(15)) as? JsonArray ?: return emptyList()
        return array.map { it.asJsonObject }.mapNotNull { job ->
            job.get(".id")?.asString?.let { it to (job.get("script")?.asString ?: "") }
        }
    }

    fun removeJob(id: String) {
        request("DELETE", "system/script/job/" + encode(id), null, Duration.ofSeconds(15))
    }

    /** Global variables of the user's script environment: name → value as text. */
    fun environment(): Map<String, String> {
        val array = request("GET", "system/script/environment", null, Duration.ofSeconds(15)) as? JsonArray ?: return emptyMap()
        return array.map { it.asJsonObject }.mapNotNull { entry ->
            val name = entry.get("name")?.asString ?: return@mapNotNull null
            name to (entry.get("value")?.takeUnless { it.isJsonNull }?.asString ?: "")
        }.toMap()
    }

    /** Log entries; each map has `.id`, `time`, `topics`, `message`. */
    fun log(): List<Map<String, String>> {
        val array = request("GET", "log?.proplist=.id,time,topics,message", null, Duration.ofSeconds(15)) as? JsonArray
            ?: return emptyList()
        return array.map { entry -> entry.asJsonObject.entrySet().associate { it.key to it.value.asString } }
    }

    private fun protocol() = RouterException("Unexpected response from the router")

    private fun string(element: JsonElement, key: String): String =
        guarded { (element as? JsonObject)?.get(key)?.takeUnless { it.isJsonNull }?.asString } ?: throw protocol()

    /** Unexpected JSON shapes become [RouterException] instead of unchecked exceptions. */
    private fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: IllegalStateException) {
        throw RouterException("Unexpected response from the router", cause = e)
    } catch (e: UnsupportedOperationException) {
        throw RouterException("Unexpected response from the router", cause = e)
    } catch (e: ClassCastException) {
        throw RouterException("Unexpected response from the router", cause = e)
    }

    private fun request(method: String, path: String, body: JsonObject?, timeout: Duration): JsonElement = guarded {
        rawRequest(method, path, body, timeout)
    }

    private fun rawRequest(method: String, path: String, body: JsonObject?, timeout: Duration): JsonElement {
        val uri = base.resolve("rest/$path")
        val builder = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("Authorization", authorization)
            .header("Accept", "application/json")
        val publisher = body?.let { HttpRequest.BodyPublishers.ofString(it.toString()) } ?: HttpRequest.BodyPublishers.noBody()
        if (body != null) builder.header("Content-Type", "application/json")
        builder.method(method, publisher)
        val response = try {
            client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: HttpTimeoutException) {
            throw RouterException("The router did not answer within ${timeout.seconds} s", cause = e)
        } catch (e: javax.net.ssl.SSLException) {
            throw RouterException("TLS error: ${e.message}. Check the TLS mode of the profile.", cause = e)
        } catch (e: IOException) {
            throw RouterException("Cannot reach ${base.host}: ${e.message ?: e.javaClass.simpleName}", cause = e)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw RouterException("Interrupted", cause = e)
        }
        val text = response.body().use { stream ->
            val bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1)
            if (bytes.size > MAX_RESPONSE_BYTES) throw RouterException("The router response exceeds ${MAX_RESPONSE_BYTES / (1024 * 1024)} MB")
            String(bytes, Charsets.UTF_8)
        }
        if (response.statusCode() == 401) throw RouterException("Authentication failed for user '${profile.user}'", 401)
        if (response.statusCode() == 403) throw RouterException("The user '${profile.user}' lacks the policy for this action", 403)
        if (response.statusCode() !in 200..299) throw RouterException(errorMessage(response.statusCode(), text), response.statusCode())
        if (text.isBlank()) return JsonObject()
        return try {
            JsonParser.parseString(text)
        } catch (e: JsonSyntaxException) {
            throw RouterException("Unexpected response from the router (not JSON)", response.statusCode(), e)
        }
    }

    private fun errorMessage(status: Int, text: String): String {
        val json = try {
            JsonParser.parseString(text) as? JsonObject
        } catch (_: JsonSyntaxException) {
            null
        }
        val detail = json?.get("detail")?.asString ?: json?.get("message")?.asString ?: text.take(200)
        return "Router returned HTTP $status: $detail"
    }

    private fun encode(segment: String) = URLEncoder.encode(segment, Charsets.UTF_8).replace("+", "%20")

    companion object {
        /** Exports and logs are well below this; anything larger is not a RouterOS answer we want in memory. */
        const val MAX_RESPONSE_BYTES = 32 * 1024 * 1024

        fun parseBase(url: String): URI {
            val trimmed = url.trim().trimEnd('/')
            val uri = try {
                URI.create("$trimmed/")
            } catch (e: IllegalArgumentException) {
                throw RouterException("Invalid router URL '$url'", cause = e)
            }
            if (uri.scheme != "http" && uri.scheme != "https" || uri.host.isNullOrEmpty()) {
                throw RouterException("The router URL must start with https:// or http:// and contain a host")
            }
            if (uri.userInfo != null) throw RouterException("Put the user name in the profile, not in the URL")
            return uri
        }

        fun sha256(certificate: X509Certificate): String =
            MessageDigest.getInstance("SHA-256").digest(certificate.encoded).joinToString("") { "%02x".format(it) }

        private fun sslContext(profile: RouterProfile): SSLContext {
            val context = SSLContext.getInstance("TLS")
            val manager: X509TrustManager = when (profile.tlsMode) {
                TlsMode.VERIFY -> TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                    .apply { init(null as java.security.KeyStore?) }
                    .trustManagers.filterIsInstance<X509TrustManager>().first()
                TlsMode.PINNED -> PinnedTrustManager(profile.pinnedSha256.lowercase().replace(":", "").trim())
                TlsMode.INSECURE -> PinnedTrustManager(null)
            }
            context.init(null, arrayOf(manager), null)
            return context
        }
    }

    /**
     * Trusts the leaf certificate whose SHA-256 equals [pin] (or any certificate when [pin] is null).
     * Host names are not checked in these modes: the pin identifies the router.
     */
    private class PinnedTrustManager(private val pin: String?) : X509ExtendedTrustManager() {
        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?) = check(chain)

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?, socket: java.net.Socket?) = check(chain)

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String?, engine: SSLEngine?) = check(chain)

        private fun check(chain: Array<out X509Certificate>) {
            if (pin == null) return
            val leaf = chain.firstOrNull() ?: throw CertificateException("no certificate")
            if (sha256(leaf) != pin) throw CertificateException("certificate fingerprint does not match the pinned SHA-256")
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String?) = throw CertificateException("not a server")

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String?, socket: java.net.Socket?) =
            throw CertificateException("not a server")

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String?, engine: SSLEngine?) =
            throw CertificateException("not a server")

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
