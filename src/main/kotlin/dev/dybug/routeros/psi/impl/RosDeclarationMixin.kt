package dev.dybug.routeros.psi.impl

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.icons.AllIcons
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.util.IncorrectOperationException
import dev.dybug.routeros.psi.RosDeclaration
import dev.dybug.routeros.psi.RosElementFactory
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosGlobalDecl
import dev.dybug.routeros.psi.RosPsiUtil
import javax.swing.Icon

abstract class RosDeclarationMixin(node: ASTNode) : ASTWrapperPsiElement(node), RosDeclaration {
    override val isGlobal: Boolean get() = this is RosGlobalDecl

    override val isFunction: Boolean get() = RosPsiUtil.isFunctionDeclaration(this)

    override fun getNameIdentifier(): PsiElement? = RosPsiUtil.declaredNameElement(this)

    override fun getName(): String? = RosPsiUtil.declaredName(this)

    override fun setName(name: String): PsiElement {
        val identifier = nameIdentifier ?: throw IncorrectOperationException("declaration has no name")
        identifier.replace(RosElementFactory.declarationName(project, name))
        return this
    }

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    /** Locals are only visible in their file (and block); globals are shared by all scripts. */
    override fun getUseScope(): SearchScope = if (isGlobal) super.getUseScope() else LocalSearchScope(containingFile)

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String? = name

        override fun getLocationString(): String = (if (isGlobal) ":global" else ":local") + " in " + (containingFile as? RosFile)?.name

        override fun getIcon(unused: Boolean): Icon = if (isFunction) AllIcons.Nodes.Function else AllIcons.Nodes.Variable
    }

    override fun getIcon(flags: Int): Icon = if (isFunction) AllIcons.Nodes.Function else AllIcons.Nodes.Variable
}
