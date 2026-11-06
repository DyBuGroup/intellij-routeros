package dev.dybug.routeros.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RosIntentionsTest : BasePlatformTestCase() {
    private fun apply(before: String, intention: String, after: String) {
        myFixture.configureByText("test.rsc", before)
        myFixture.launchAction(myFixture.findSingleIntention(intention))
        myFixture.checkResult(after)
    }

    fun testToSlashPath() = apply("/ip fire<caret>wall filter add chain=input\n", "Convert to slash path (/ip/address)", "/ip/firewall/filter/add chain=input\n")

    fun testToSpacePath() = apply("/ip/fire<caret>wall/filter\n", "Convert to space path (/ip address)", "/ip firewall filter\n")

    fun testQuote() = apply("/system identity set name=rou<caret>ter1\n", "Quote the value", "/system identity set name=\"router1\"\n")

    fun testUnquote() = apply("/system identity set name=\"rou<caret>ter1\"\n", "Remove quotes from the value", "/system identity set name=router1\n")

    fun testWrapOnError() = apply(
        "/tool fe<caret>tch url=\"https://x\"\n",
        "Wrap in :do { } on-error={ }",
        ":do { /tool fetch url=\"https://x\" } on-error={ :log warning \"failed\" }\n",
    )
}
