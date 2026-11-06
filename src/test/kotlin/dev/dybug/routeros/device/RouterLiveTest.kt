package dev.dybug.routeros.device

import dev.dybug.routeros.device.sync.RosDeviceSchemaWalker
import dev.dybug.routeros.device.validate.RosRemoteValidator
import junit.framework.TestCase

/** Runs only with ROS_URL (and ROS_ALLOW_HTTP=true for http://) pointing at a disposable router. */
class RouterLiveTest : TestCase() {
    private val client: RouterRestClient? by lazy {
        val url = System.getenv("ROS_URL")?.takeIf { it.isNotBlank() } ?: return@lazy null
        if (url.startsWith("http://") && System.getenv("ROS_ALLOW_HTTP") != "true") return@lazy null
        val profile = RouterProfile().apply {
            this.url = url
            user = System.getenv("ROS_USER") ?: "admin"
            tlsMode = if (System.getenv("ROS_TLS_INSECURE") == "true") TlsMode.INSECURE else TlsMode.VERIFY
            timeoutSeconds = 60
        }
        RouterRestClient(profile, System.getenv("ROS_PASSWORD").orEmpty())
    }

    fun testExecuteAndErrors() {
        val client = client ?: return
        assertTrue(client.version().startsWith("7."))
        assertEquals("3", client.execute(":put (1+2)").trim())
        val output = client.execute(":put 1\n:foo")
        assertEquals(2, RosRemoteValidator.parseProblems(output.lines().last()).single().line)
    }

    fun testValidation() {
        val client = client ?: return
        assertTrue(RosRemoteValidator.validateScript(client, ":put 1\n").isEmpty())
        assertEquals(2, RosRemoteValidator.validateScript(client, ":put 1\n:put (1+\n").first().line)
        val problems = RosRemoteValidator.validateExport(client, "/ip address\nadd address=1.2.3.4/24 interfacee=x\n")
        assertEquals(2, problems.first().line)
    }

    fun testWalkerOnSmallSubtree() {
        val client = client ?: return
        val nodes = RosDeviceSchemaWalker(client, emptySet(), maxNodes = 30).walk(null)
        assertTrue(nodes.size() > 0)
    }
}
