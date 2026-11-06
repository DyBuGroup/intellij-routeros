package dev.dybug.routeros.device

import dev.dybug.routeros.device.sync.RosDeviceSchemaWalker
import dev.dybug.routeros.device.validate.RosRemoteValidator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dybug.routeros.device.debug.RosInstrumenter
import dev.dybug.routeros.device.debug.RouterDebugController
import dev.dybug.routeros.psi.RosFile

/** Runs only with ROS_URL (and ROS_ALLOW_HTTP=true for http://) pointing at a disposable router. */
class RouterLiveTest : BasePlatformTestCase() {
    private val client: RouterRestClient? by lazy {
        val url = System.getenv("ROS_URL")?.takeIf { it.isNotBlank() } ?: return@lazy null
        if (url.startsWith("http://") && System.getenv("ROS_ALLOW_HTTP") != "true") return@lazy null
        val profile = RouterProfile().apply {
            this.url = url
            user = System.getenv("ROS_USER") ?: "admin"
            tlsMode = if (System.getenv("ROS_TLS_INSECURE") == "true" || url.startsWith("http://")) TlsMode.INSECURE else TlsMode.VERIFY
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

    /** Spike from the plan: pause at a breakpoint, read variables, step, resume — over REST only. */
    fun testDebuggerRendezvous() {
        val client = client ?: return
        val file = myFixture.configureByText("d.rsc", ":local a 41\n:set a (\$a + 1)\n:put \$a\n") as RosFile
        val instrumenter = RosInstrumenter("live" + System.nanoTime().toString().takeLast(6))
        val controller = RouterDebugController(client, instrumenter)
        controller.prepare(listOf(1))
        val run = RouterBackgroundRun(client, "ide-dbg-test")
        run.start(instrumenter.instrument(file).text)
        val output = java.util.concurrent.CompletableFuture.supplyAsync { run.await(30_000) }
        try {
            val first = waitForPause(controller)
            assertEquals(2, first.line)
            assertEquals(41, first.variables.get("a").asInt)
            controller.resume(step = true)
            val second = waitForPause(controller, notLine = 2)
            assertEquals(3, second.line)
            assertEquals(42, second.variables.get("a").asInt)
            controller.resume(step = false)
            assertEquals("42", output.get(30, java.util.concurrent.TimeUnit.SECONDS).trim())
        } finally {
            controller.cleanUp()
            run.cleanUp()
        }
    }

    private fun waitForPause(controller: RouterDebugController, notLine: Int = -1): RouterDebugController.Pause {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            controller.pause()?.takeIf { it.line != notLine }?.let { return it }
            Thread.sleep(100)
        }
        error("no pause within 15 s")
    }

    fun testBackgroundRunCapturesOutput() {
        val client = client ?: return
        val run = RouterBackgroundRun(client)
        try {
            run.start(":put \"a\"; :delay 1s; :put \"b\"")
            assertEquals(listOf("a", "b"), run.await(20_000).lines().map { it.trim() }.filter { it.isNotEmpty() })
        } finally {
            run.cleanUp()
        }
    }
}
