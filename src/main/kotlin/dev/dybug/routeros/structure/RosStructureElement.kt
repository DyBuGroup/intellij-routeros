package dev.dybug.routeros.structure

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.util.PsiTreeUtil
import dev.dybug.routeros.psi.RosBlock
import dev.dybug.routeros.psi.RosCommand
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosGlobalDecl
import dev.dybug.routeros.psi.RosLocalDecl
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosScriptCommand
import dev.dybug.routeros.psi.RosSetStatement
import dev.dybug.routeros.psi.RosFunctionCall
import javax.swing.Icon

sealed class RosStructureElement(protected val element: NavigatablePsiElement) : StructureViewTreeElement {
    override fun getValue(): Any = element

    override fun navigate(requestFocus: Boolean) = element.navigate(requestFocus)

    override fun canNavigate(): Boolean = element.canNavigate()

    override fun canNavigateToSource(): Boolean = element.canNavigateToSource()

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String = text()

        override fun getLocationString(): String? = location()

        override fun getIcon(unused: Boolean): Icon = icon()
    }

    protected abstract fun text(): String

    protected open fun location(): String? = null

    protected abstract fun icon(): Icon

    class FileElement(private val file: RosFile) : RosStructureElement(file) {
        override fun text(): String = file.name

        override fun icon(): Icon = file.getIcon(0) ?: AllIcons.FileTypes.Text

        /** Export sections group the commands that follow a bare menu path; declarations stay at top level. */
        override fun getChildren(): Array<TreeElement> {
            val result = mutableListOf<TreeElement>()
            var section: SectionElement? = null
            for (child in file.children) {
                when {
                    child is RosCommand && RosPsiUtil.isMenuContextLine(child) -> {
                        section = SectionElement(child).also { result += it }
                    }
                    child is RosLocalDecl || child is RosGlobalDecl -> {
                        section = null
                        result += DeclarationElement(child as NavigatablePsiElement)
                    }
                    child is RosCommand -> {
                        val item = CommandElement(child)
                        if (section != null && RosPsiUtil.isRelativeCommand(child)) {
                            section.items += item
                        } else {
                            section = null
                            result += item
                        }
                    }
                    child is RosScriptCommand || child is RosSetStatement || child is RosFunctionCall -> section = null
                }
            }
            return result.toTypedArray()
        }
    }

    class SectionElement(private val command: RosCommand) : RosStructureElement(command as NavigatablePsiElement) {
        val items = mutableListOf<CommandElement>()

        override fun text(): String = "/" + RosPsiUtil.resolvedPath(command).joinToString(" ")

        override fun location(): String? = if (items.isEmpty()) null else items.size.toString()

        override fun icon(): Icon = AllIcons.Nodes.Folder

        override fun getChildren(): Array<TreeElement> = items.toTypedArray()
    }

    class CommandElement(private val command: RosCommand) : RosStructureElement(command as NavigatablePsiElement) {
        override fun text(): String = summary(command)

        override fun icon(): Icon = AllIcons.Nodes.Property

        override fun getChildren(): Array<TreeElement> = TreeElement.EMPTY_ARRAY
    }

    class DeclarationElement(private val decl: NavigatablePsiElement) : RosStructureElement(decl) {
        val isFunction: Boolean = RosPsiUtil.isFunctionDeclaration(decl)

        override fun text(): String = RosPsiUtil.declaredName(decl) ?: decl.text.take(40)

        override fun location(): String = if (decl is RosGlobalDecl) ":global" else ":local"

        override fun icon(): Icon = if (isFunction) AllIcons.Nodes.Function else AllIcons.Nodes.Variable

        /** Functions show the functions and variables declared directly in their body. */
        override fun getChildren(): Array<TreeElement> {
            if (!isFunction) return TreeElement.EMPTY_ARRAY
            val body = RosPsiUtil.namedArgument(decl, "do")?.let(RosPsiUtil::argumentValue) as? RosBlock
                ?: return TreeElement.EMPTY_ARRAY
            return PsiTreeUtil.getChildrenOfAnyType(body, RosLocalDecl::class.java, RosGlobalDecl::class.java)
                .filter { RosPsiUtil.isFunctionDeclaration(it) }
                .map { DeclarationElement(it as NavigatablePsiElement) }
                .toTypedArray()
        }
    }

    companion object {
        private val KEY_PROPERTIES = listOf("name", "address", "list", "chain", "action", "interface", "dst-address", "comment")

        fun summary(command: RosCommand): String {
            val path = command.path?.let(RosPsiUtil::pathSegments).orEmpty()
            val head = if (command.path != null && RosPsiUtil.isAbsolute(command.path!!)) {
                "/" + path.joinToString(" ")
            } else {
                path.joinToString(" ")
            }
            val named = RosPsiUtil.namedArguments(command)
            val picked = KEY_PROPERTIES.mapNotNull { key -> named.firstOrNull { RosPsiUtil.argumentName(it) == key } }
                .ifEmpty { named }
                .take(3)
            val args = picked.joinToString(" ") { arg ->
                val value = RosPsiUtil.argumentValueText(arg).orEmpty().replace('\n', ' ')
                RosPsiUtil.argumentName(arg) + "=" + if (value.length > 40) value.take(37) + "..." else value
            }
            return listOf(head, args).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { command.text.take(60) }
        }
    }
}
