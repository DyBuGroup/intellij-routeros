package dev.dybug.routeros.device

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/** A tiny stand-in for the RouterOS REST API: routes "METHOD /rest/path" to canned responses. */
class FakeRouter : AutoCloseable {
    data class Request(val method: String, val path: String, val body: String, val authorization: String?)

    val requests = mutableListOf<Request>()
    val routes = mutableMapOf<String, (Request) -> Pair<Int, String>>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    init {
        server.createContext("/") { exchange ->
            val request = Request(
                exchange.requestMethod,
                exchange.requestURI.rawPath + (exchange.requestURI.rawQuery?.let { "?$it" } ?: ""),
                exchange.requestBody.readBytes().toString(Charsets.UTF_8),
                exchange.requestHeaders.getFirst("Authorization"),
            )
            synchronized(requests) { requests += request }
            val handler = routes["${request.method} ${request.path.substringBefore('?')}"]
            val (status, body) = handler?.invoke(request) ?: (404 to """{"error":404,"message":"Not Found"}""")
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
    }

    val url: String get() = "http://127.0.0.1:${server.address.port}"

    fun profile() = RouterProfile().apply {
        name = "fake"
        url = this@FakeRouter.url
        user = "ide"
        tlsMode = TlsMode.INSECURE
        timeoutSeconds = 10
    }

    fun client(password: String = "secret") = RouterRestClient(profile(), password)

    override fun close() = server.stop(0)
}
