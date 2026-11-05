package dev.dybug.routeros.injection

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.PsiElement
import dev.dybug.routeros.RosLanguage
import dev.dybug.routeros.psi.RosCommand
import dev.dybug.routeros.psi.RosNamedArgument
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosString
import dev.dybug.routeros.psi.impl.RosStringMixin

/**
 * Injects RouterOS script into string values that hold scripts, such as
 * `/system script add source="…"` and `/system scheduler add on-event="…"`.
 */
class RosScriptInjector : MultiHostInjector {
    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val string = context as? RosStringMixin ?: return
        if (!string.isValidHost) return
        val argument = string.parent as? RosNamedArgument ?: return
        if (RosPsiUtil.argumentValue(argument) !== string) return
        val name = RosPsiUtil.argumentName(argument)
        if (!isScriptArgument(argument, name)) return
        registrar.startInjecting(RosLanguage)
            .addPlace(null, null, string, RosPsiUtil.contentRange(string))
            .doneInjecting()
    }

    private fun isScriptArgument(argument: RosNamedArgument, name: String): Boolean {
        if (name in ALWAYS_SCRIPT) return true
        if (name != "source" && name != "on-event") return false
        val command = argument.parent as? RosCommand ?: return false
        val path = RosPsiUtil.resolvedPath(command)
        return path.take(2) == listOf("system", "script") || path.take(2) == listOf("system", "scheduler")
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(RosString::class.java)

    private companion object {
        /** Properties that hold a script in any menu (netwatch, DHCP, PPP, hotspot, …). */
        val ALWAYS_SCRIPT = setOf(
            "on-up", "on-down", "up-script", "down-script", "test-script", "lease-script",
            "on-login", "on-logout", "script",
        )
    }
}
