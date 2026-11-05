package dev.dybug.routeros

import com.intellij.lang.Language

object RosLanguage : Language("RouterOS") {
    private fun readResolve(): Any = RosLanguage

    override fun getDisplayName(): String = "RouterOS Script"

    override fun isCaseSensitive(): Boolean = true
}
