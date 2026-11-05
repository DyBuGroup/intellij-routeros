package dev.dybug.routeros.psi

import com.intellij.psi.tree.IElementType
import dev.dybug.routeros.RosLanguage
import org.jetbrains.annotations.NonNls

class RosTokenType(@NonNls debugName: String) : IElementType(debugName, RosLanguage) {
    override fun toString(): String = "RosTokenType." + super.toString()
}
