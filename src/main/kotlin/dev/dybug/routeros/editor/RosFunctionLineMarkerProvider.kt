package dev.dybug.routeros.editor

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.psi.RosDeclaration
import javax.swing.Icon

/** Gutter icon on function declarations (`:global f do={…}`). Registered on the name leaf, as recommended. */
class RosFunctionLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName(): String = RosBundle.message("linemarker.function")

    override fun getIcon(): Icon = AllIcons.Nodes.Function

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.firstChild != null) return null
        val declaration = element.parent as? RosDeclaration ?: return null
        if (declaration.nameIdentifier !== element || !declaration.isFunction) return null
        val name = declaration.name ?: return null
        return LineMarkerInfo(
            element,
            element.textRange,
            AllIcons.Nodes.Function,
            { RosBundle.message(if (declaration.isGlobal) "linemarker.global.function" else "linemarker.local.function", name) },
            null,
            GutterIconRenderer.Alignment.LEFT,
            { RosBundle.message("linemarker.function") },
        )
    }
}
