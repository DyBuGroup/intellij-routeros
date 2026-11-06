package dev.dybug.routeros.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RosPsiUtilTest : BasePlatformTestCase() {
    fun testResolvedPathUsesMenuContext() {
        val file = myFixture.configureByText(
            "test.rsc",
            "/ip firewall filter\nadd chain=input\n/ip address\n.. route print\n/interface/bridge/port/add bridge=b\n",
        )
        val commands = PsiTreeUtil.getChildrenOfTypeAsList(file, RosCommand::class.java)
        assertEquals(listOf("ip", "firewall", "filter", "add"), RosPsiUtil.resolvedPath(commands[1]))
        assertEquals(listOf("ip", "route", "print"), RosPsiUtil.resolvedPath(commands[3]))
        assertEquals(listOf("interface", "bridge", "port", "add"), RosPsiUtil.resolvedPath(commands[4]))
        assertTrue(RosPsiUtil.isMenuContextLine(commands[0]))
        assertFalse(RosPsiUtil.isMenuContextLine(commands[1]))
    }

    fun testMenuContextPersistsIntoBlocks() {
        val file = myFixture.configureByText("test.rsc", "/queue simple\n:foreach q in=[find] do={ :put [get \$q name] }\n")
        val inner = PsiTreeUtil.findChildrenOfType(file, RosCommand::class.java).last()
        assertEquals(listOf("queue", "simple", "get"), RosPsiUtil.resolvedPath(inner))
    }

    fun testDeclarationsAndArguments() {
        val file = myFixture.configureByText("test.rsc", ":global \"my-var\" 1\n:local f do={ :return 1 }\nadd name=\"a b\" disabled=yes\n")
        val global = PsiTreeUtil.findChildOfType(file, RosGlobalDecl::class.java)!!
        assertEquals("my-var", RosPsiUtil.declaredName(global))
        assertFalse(RosPsiUtil.isFunctionDeclaration(global))
        val local = PsiTreeUtil.findChildOfType(file, RosLocalDecl::class.java)!!
        assertEquals("f", RosPsiUtil.declaredName(local))
        assertTrue(RosPsiUtil.isFunctionDeclaration(local))
        val add = PsiTreeUtil.getChildrenOfTypeAsList(file, RosCommand::class.java).last()
        assertEquals(listOf("name", "disabled"), RosPsiUtil.namedArguments(add).map(RosPsiUtil::argumentName))
        assertEquals("a b", RosPsiUtil.namedArgument(add, "name")?.let(RosPsiUtil::argumentValueText))
    }

    fun testCommandInvocationsDoNotChangeMenuContext() {
        val file = myFixture.configureByText("test.rsc", "/system script\n/ip address print\n/system reboot\nadd name=x\n")
        val commands = PsiTreeUtil.getChildrenOfTypeAsList(file, RosCommand::class.java)
        assertTrue(RosPsiUtil.isMenuContextLine(commands[0]))
        assertFalse(RosPsiUtil.isMenuContextLine(commands[1]))
        assertFalse(RosPsiUtil.isMenuContextLine(commands[2]))
        assertEquals(listOf("system", "script", "add"), RosPsiUtil.resolvedPath(commands[3]))
    }
}
