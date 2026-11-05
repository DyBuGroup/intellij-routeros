package dev.dybug.routeros.structure

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RosStructureViewTest : BasePlatformTestCase() {
    fun testExportSectionsAndFunctions() {
        myFixture.configureByText(
            "test.rsc",
            """
            /interface bridge
            add name=bridge1 comment="LAN"
            /ip address
            add address=192.168.88.1/24 interface=bridge1
            :global counter 0
            :global greet do={
                :local inner do={ :return 1 }
                :return ("hi " . ${'$'}1)
            }
            """.trimIndent(),
        )
        myFixture.testStructureView { component ->
            PlatformTestUtil.expandAll(component.tree)
            PlatformTestUtil.assertTreeEqual(
                component.tree,
                """
                -test.rsc
                 -/interface bridge
                  add name=bridge1 comment=LAN
                 -/ip address
                  add address=192.168.88.1/24 interface=bridge1
                 counter
                 -greet
                  inner
                """.trimIndent() + "\n",
            )
        }
    }
}
