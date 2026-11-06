package dev.dybug.routeros.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.dybug.routeros.highlighting.RosAnnotatorTest

class RosInspectionsTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(*RosAnnotatorTest.ALL_INSPECTIONS.map { it.getDeclaredConstructor().newInstance() }.toTypedArray())
    }

    private fun check(text: String) {
        myFixture.configureByText("test.rsc", text)
        myFixture.checkHighlighting(true, false, true)
    }

    fun testUndefinedVariable() = check(
        ":local a 1\n:put \$a\n:put \$<weak_warning descr=\"Variable 'b' is not declared\">b</weak_warning>\n" +
            ":global f do={ :put \$param }\n:put \$f\n",
    )

    fun testGlobalDeclaredElsewhere() {
        myFixture.addFileToProject("lib.rsc", ":global counter 0\n")
        check(":put \$<warning descr=\"Global 'counter' is declared in another script; declare it with ':global counter' to use it here\">counter</warning>\n")
    }

    fun testUnusedLocal() = check(":local <warning descr=\"Local variable 'x' is never used\">x</warning> 1\n:local y 2\n:put \$y\n")

    fun testSyntaxProblems() = check(
        """
        /ip address add address=1.2.3.4/24 interface<warning descr="Whitespace is not allowed before '='"> </warning>=ether1
        :local <warning descr="Variable name 'my-var' must be quoted">my-var</warning> 1
        :put ${'$'}"my-var"
        :local arr <warning descr="'{}' is not an empty array">{}</warning>
        :put ${'$'}arr
        <warning descr="':if' needs a do={ … } block">:if</warning> (true)
        :local f do={ :return 1; <warning descr="Unreachable code after ':return'">:put 2</warning> }
        :put ${'$'}f
        /system identity set name=x <warning descr="A comment must start a line or follow ';'">#</warning> note
        /system identity set name=y; # fine
        """.trimIndent() + "\n",
    )

    fun testMissingMandatory() = check(
        "<weak_warning descr=\"Missing mandatory properties: address, interface\">/ip address add</weak_warning> comment=x\n" +
            "/ip address add address=1.2.3.4/24 interface=ether1\n/ip address add copy-from=0\n",
    )

    fun testQuickFixDeclareLocal() {
        myFixture.configureByText("test.rsc", ":put \$<caret>missing\n")
        val fix = myFixture.findSingleIntention("Declare ':local missing'")
        myFixture.launchAction(fix)
        myFixture.checkResult(":local missing\n:put \$missing\n")
    }

    fun testQuickFixDidYouMean() {
        myFixture.configureByText("test.rsc", "/ip address add interfce<caret>=ether1 address=1.2.3.4/24\n")
        myFixture.launchAction(myFixture.findSingleIntention("Change to 'interface'"))
        myFixture.checkResult("/ip address add interface=ether1 address=1.2.3.4/24\n")
    }

    fun testV6Property() = check(
        "/ip route add dst-address=0.0.0.0/0 gateway=1.1.1.1 <warning descr=\"'routing-mark' is RouterOS v6 syntax; use 'routing-table'\">routing-mark</warning>=x\n",
    )

    fun testSetTargets() = check(
        ":local a 1\n:set a 2\n:put \$a\n:set <warning descr=\"Variable 'b' must be declared with :local or :global before :set\">b</warning> 3\n:local e ({})\n:put \$e\n",
    )
}
