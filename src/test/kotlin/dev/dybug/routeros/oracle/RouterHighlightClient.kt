package dev.dybug.routeros.oracle

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

/** Minimal client for `/rest/console/inspect request=highlight`, used only by the oracle tests. */
class RouterHighlightClient(baseUrl: String, user: String, password: String) {
    private val endpoint = URI.create(baseUrl.trimEnd('/') + "/rest/console/inspect")
    private val authorization = "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .proxy(HttpClient.Builder.NO_PROXY)
        .build()

    /** Returns one highlight class per input character. Non-ASCII characters are sent as '?'. */
    fun highlight(input: String): List<String> {
        val ascii = input.map { if (it.code in 0..127) it else '?' }.joinToString("")
        val body = JsonObject().apply {
            addProperty("request", "highlight")
            addProperty("input", ascii)
        }
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Authorization", authorization)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "Router returned HTTP ${response.statusCode()}" }
        val array = JsonParser.parseString(response.body()) as JsonArray
        val highlight = array.firstOrNull { it.asJsonObject.get("type")?.asString == "highlight" }
            ?.asJsonObject?.get("highlight")?.asString
            ?: error("No highlight entry in router response")
        return highlight.split(',')
    }

    companion object {
        fun fromEnvironment(): RouterHighlightClient? {
            val url = System.getenv("ROS_URL")?.takeIf { it.isNotBlank() } ?: return null
            require(url.startsWith("https://") || System.getenv("ROS_ALLOW_HTTP") == "true") {
                "ROS_URL must use https:// (set ROS_ALLOW_HTTP=true to accept plain HTTP for a disposable lab router)"
            }
            return RouterHighlightClient(url, System.getenv("ROS_USER") ?: "admin", System.getenv("ROS_PASSWORD") ?: "")
        }
    }
}
