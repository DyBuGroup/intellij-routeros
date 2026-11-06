package dev.dybug.routeros.device

import com.google.gson.JsonParser
import dev.dybug.routeros.device.log.RouterLogTailer
import dev.dybug.routeros.device.sync.RosDeviceSchemaWalker
import dev.dybug.routeros.device.validate.RosRemoteValidator
import junit.framework.TestCase
import java.util.Base64

class RouterRestClientTest : TestCase() {
    fun testExecuteSendsScriptAndAuth() = FakeRouter().use { router ->
        router.routes["POST /rest/execute"] = { 200 to """{"ret":"hello\r\nworld"}""" }
        assertEquals("hello\r\nworld", router.client().execute(":put hello"))
        val request = router.requests.single()
        assertEquals("Basic " + Base64.getEncoder().encodeToString("ide:secret".toByteArray()), request.authorization)
        val body = JsonParser.parseString(request.body).asJsonObject
        assertEquals(":put hello", body.get("script").asString)
        assertEquals("yes", body.get("as-string").asString)
    }

    fun testErrorsAreMappedWithoutSecrets() = FakeRouter().use { router ->
        router.routes["GET /rest/system/resource"] = { 401 to "" }
        val e1 = assertThrows { router.client("topsecret").resource() }
        assertTrue(e1.message!!.contains("Authentication failed"))
        assertFalse(e1.message!!.contains("topsecret"))
        router.routes["GET /rest/system/resource"] = { 400 to """{"error":400,"message":"Bad Request","detail":"no such command"}""" }
        assertTrue(assertThrows { router.client().resource() }.message!!.contains("no such command"))
    }

    fun testHighlightReplacesNonAscii() = FakeRouter().use { router ->
        router.routes["POST /rest/console/inspect"] = { 200 to """[{"highlight":"dir,cmd,error","type":"highlight"}]""" }
        assertEquals(listOf("dir", "cmd", "error"), router.client().highlight(":é"))
        assertEquals(":?", JsonParser.parseString(router.requests.single().body).asJsonObject.get("input").asString)
    }

    fun testUrlValidation() {
        assertThrows { RouterRestClient.parseBase("ftp://router") }
        assertThrows { RouterRestClient.parseBase("https://admin:pw@router") }
        assertEquals("https://router/", RouterRestClient.parseBase("https://router/").toString())
    }

    fun testValidators() {
        assertEquals(
            listOf(dev.dybug.routeros.device.validate.RouterProblem(2, 10, "syntax error")),
            RosRemoteValidator.parseProblems("#line 2\nsyntax error (line 2 column 10)\nScript Error: found 1 error(s)"),
        )
        assertEquals(2 to 3, RosRemoteValidator.lineColumn("ab\ncdef", 5))
    }

    fun testExportValidationUploadsAndDeletes() = FakeRouter().use { router ->
        router.routes["PUT /rest/file"] = { 200 to """{".id":"*1"}""" }
        router.routes["POST /rest/execute"] = { 200 to """{"ret":"bad command name foo (line 3 column 1)"}""" }
        router.routes.putIfAbsent("DELETE /rest/file/x", { 204 to "" })
        val problems = RosRemoteValidator.validateExport(router.client(), "/ip address\nadd\nfoo\n")
        assertEquals(3, problems.single().line)
        assertTrue(router.requests.any { it.method == "DELETE" && it.path.startsWith("/rest/file/ide-validate-") })
        val script = JsonParser.parseString(router.requests.first { it.path == "/rest/execute" }.body).asJsonObject.get("script").asString
        assertTrue(script, script.contains("dry-run"))
    }

    fun testSchemaWalker() = FakeRouter().use { router ->
        router.routes["POST /rest/console/inspect"] = { request ->
            val path = JsonParser.parseString(request.body).asJsonObject.get("path").asString
            200 to when (path) {
                "" -> """[{"name":"x","node-type":"dir","type":"child"},{"name":"beep","node-type":"cmd","type":"child"}]"""
                "x" -> """[{"name":"x","node-type":"dir","type":"self"},{"name":"add","node-type":"cmd","type":"child"},{"name":"go","node-type":"cmd","type":"child"}]"""
                "x,add" -> """[{"name":"name","node-type":"arg","type":"child"}]"""
                "x,go" -> """[{"name":"speed","node-type":"arg","type":"child"}]"""
                else -> "[]"
            }
        }
        val nodes = RosDeviceSchemaWalker(router.client(), setOf("beep")).walk(null)
        assertEquals("dir", nodes.getAsJsonObject("x").get("kind").asString)
        assertEquals("name", nodes.getAsJsonObject("x").getAsJsonArray("args")[0].asJsonObject.get("n").asString)
        assertEquals("cmd", nodes.getAsJsonObject("x/go").get("kind").asString)
        assertNull(nodes.get("beep"))
    }

    fun testLogTailerReportsOnlyNewEntries() = FakeRouter().use { router ->
        var entries = """[{".id":"*9","time":"t","topics":"info","message":"old"}]"""
        router.routes["GET /rest/log"] = { 200 to entries }
        val seen = mutableListOf<String>()
        val tailer = RouterLogTailer(router.client(), 60_000, onEntry = { _, _, message -> seen += message }, onError = {})
        tailer.start()
        entries = """[{".id":"*9","time":"t","topics":"info","message":"old"},{".id":"*A","time":"t","topics":"script","message":"new"}]"""
        tailer.flush()
        tailer.stop()
        assertEquals(listOf("new"), seen)
    }

    private fun assertThrows(block: () -> Unit): RouterException {
        try {
            block()
        } catch (e: RouterException) {
            return e
        }
        fail("expected RouterException")
        error("unreachable")
    }
}
