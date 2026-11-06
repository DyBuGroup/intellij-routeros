package dev.dybug.routeros.completion

import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import dev.dybug.routeros.psi.RosNamedArgument
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.schema.RosSchemaContext
import dev.dybug.routeros.schema.RosSchemaService

/** Ctrl+P on a command line lists the properties the command accepts; mandatory ones first. */
class RosParameterInfoHandler : ParameterInfoHandler<PsiElement, RosParameterInfoHandler.Item> {
    class Item(val names: List<String>, val mandatory: Set<String>)

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement? {
        val owner = ownerAt(context.file, context.offset) ?: return null
        val schema = RosSchemaService.schema() ?: return null
        val arguments = RosSchemaContext.argumentsFor(owner, schema)
        if (arguments.isEmpty()) return null
        val mandatory = arguments.values.filter { it.mandatory }.map { it.name }.toSet()
        val names = arguments.keys.sortedWith(compareBy({ it !in mandatory }, { it }))
        context.itemsToShow = arrayOf(Item(names, mandatory))
        return owner
    }

    override fun showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): PsiElement? =
        ownerAt(context.file, context.offset)

    override fun updateParameterInfo(parameterOwner: PsiElement, context: UpdateParameterInfoContext) {
        val leaf = context.file.findElementAt(context.offset)
        val argument = PsiTreeUtil.getParentOfType(leaf, RosNamedArgument::class.java)
        val name = argument?.let(RosPsiUtil::argumentName)
        val item = context.objectsToView.firstOrNull() as? Item
        context.setCurrentParameter(if (name == null || item == null) -1 else item.names.indexOf(name))
    }

    override fun updateUI(p: Item, context: ParameterInfoUIContext) {
        val current = context.currentParameterIndex
        val text = StringBuilder()
        var start = -1
        var end = -1
        p.names.forEachIndexed { index, name ->
            if (index > 0) text.append(", ")
            if (index == current) start = text.length
            text.append(name)
            if (name in p.mandatory) text.append('*')
            if (index == current) end = text.length
        }
        context.setupUIComponentPresentation(text.toString(), start, end, false, false, false, context.defaultParameterColor)
    }

    private fun ownerAt(file: PsiFile, offset: Int): PsiElement? {
        val leaf = file.findElementAt(offset) ?: file.findElementAt(offset - 1) ?: return null
        return RosSchemaContext.owningStatement(leaf)
    }
}
