package dev.dybug.routeros.psi

import com.intellij.psi.tree.IElementType
import dev.dybug.routeros.RosLanguage
import org.jetbrains.annotations.NonNls

class RosElementType(@NonNls debugName: String) : IElementType(debugName, RosLanguage)
