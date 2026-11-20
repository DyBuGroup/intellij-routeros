package dev.dybug.routeros.completion

import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.util.ThrowableRunnable

class RosCompletionAutoPopupTest : BasePlatformTestCase() {
    private lateinit var tester: CompletionAutoPopupTester

    override fun setUp() {
        super.setUp()
        tester = CompletionAutoPopupTester(myFixture)
    }

    override fun runInDispatchThread() = false

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        tester.runWithAutoPopupEnabled(testRunnable)
    }

    private fun configure(text: String) {
        EdtTestUtil.runInEdtAndWait<Throwable> { myFixture.configureByText("test.rsc", text) }
    }

    fun testNoPopupInComment() {
        configure(":local x [:toarray 1]\n#<caret>\n")
        tester.typeWithPauses(" to")
        assertNull(tester.lookup)
    }

    fun testPopupStillWorksInCode() {
        configure(":local x [:toarray 1]\n<caret>\n")
        tester.typeWithPauses(":to")
        assertNotNull(tester.lookup)
    }
}
