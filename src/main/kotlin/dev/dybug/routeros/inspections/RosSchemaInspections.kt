package dev.dybug.routeros.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.openapi.util.text.StringUtil
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.psi.RosCommand
import dev.dybug.routeros.psi.RosNamedArgument
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosString
import dev.dybug.routeros.psi.RosTypes
import dev.dybug.routeros.psi.RosVariable
import dev.dybug.routeros.psi.RosWhereClause
import dev.dybug.routeros.schema.RosSchema
import dev.dybug.routeros.schema.RosSchemaContext
import dev.dybug.routeros.schema.RosSchemaService
import dev.dybug.routeros.schema.SchemaArg
import dev.dybug.routeros.schema.ValueTypeDetector

/** Shared lookup: the documented arguments of the command owning a named argument, when names can be checked. */
private fun checkedArguments(command: RosCommand, schema: RosSchema): Map<String, SchemaArg>? {
    val target = RosSchemaContext.target(command, schema)
    if (!target.isKnown) return null
    if (target.command == null && target.verb != "add" && target.verb != "set") return null
    return schema.argumentsOf(target).takeIf { it.isNotEmpty() }
}

class RosUnknownPropertyInspection : RosInspectionBase() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        if (element !is RosNamedArgument) return
        val key = element.firstChild ?: return
        if (key.elementType != RosTypes.ARG_NAME) return
        val command = element.parent as? RosCommand ?: return
        val schema = RosSchemaService.schema(element.project) ?: return
        val arguments = checkedArguments(command, schema) ?: return
        val name = key.text
        if (name in arguments || name.contains('.') || name in schema.builtins.universalArguments) return
        val menuPath = RosPsiUtil.resolvedPath(command).dropLast(1).joinToString("/")
        if ("$menuPath:$name" in RosDeprecatedPropertyInspection.V6_REPLACEMENTS) return
        val suggestion = arguments.keys.filter { StringUtil.commonPrefixLength(it, name) >= 2 || distance(it, name) <= 2 }
            .minByOrNull { distance(it, name) }
            ?.takeIf { distance(it, name) <= maxOf(2, name.length / 3) }
        val fixes = listOfNotNull(suggestion?.let { RosQuickFixes.ReplaceText("fix.rename.property", it, it) })
        holder.registerProblem(
            key, RosBundle.message("inspection.unknown.property", name), ProblemHighlightType.WEAK_WARNING, *fixes.toTypedArray(),
        )
    }

    private fun distance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var previous = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, previous + if (a[i - 1] == b[j - 1]) 0 else 1)
                previous = temp
            }
        }
        return dp[b.length]
    }
}

class RosValueTypeInspection : RosInspectionBase() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        if (element !is RosNamedArgument) return
        val command = element.parent as? RosCommand ?: return
        val schema = RosSchemaService.schema(element.project) ?: return
        val target = RosSchemaContext.target(command, schema)
        if (!target.isKnown) return
        val arg = schema.argumentsOf(target)[RosPsiUtil.argumentName(element)] ?: return
        val value = RosPsiUtil.argumentValue(element) ?: return
        if (value.elementType != RosTypes.WORD && value !is RosString) return
        // only plain literals: escapes would need decoding and interpolation is dynamic
        if (value is RosString && value.node.getChildren(null).any { it.elementType != RosTypes.STRING_START && it.elementType != RosTypes.STRING_END && it.elementType != RosTypes.STRING_TEXT }) return
        val text = RosPsiUtil.argumentValueText(element) ?: return
        val problem = ValueTypeDetector.problem(text, arg.type) ?: return
        holder.registerProblem(value, RosBundle.message("inspection.bad.value", arg.name, problem), ProblemHighlightType.WEAK_WARNING)
    }
}

class RosDeprecatedPropertyInspection : RosInspectionBase() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        if (element !is RosNamedArgument) return
        val key = element.firstChild?.takeIf { it.elementType == RosTypes.ARG_NAME } ?: return
        val command = element.parent as? RosCommand ?: return
        val schema = RosSchemaService.schema(element.project) ?: return
        val path = RosPsiUtil.resolvedPath(command).joinToString("/")
        V6_REPLACEMENTS[path.substringBeforeLast('/') + ":" + key.text]?.let { replacement ->
            holder.registerProblem(
                key, RosBundle.message("inspection.v6.property", key.text, replacement), ProblemHighlightType.LIKE_DEPRECATED,
                RosQuickFixes.ReplaceText("fix.rename.property", replacement, replacement),
            )
            return
        }
        val arg = schema.argumentsOf(RosSchemaContext.target(command, schema))[key.text] ?: return
        if (arg.deprecated) {
            holder.registerProblem(key, RosBundle.message("inspection.deprecated.property", key.text), ProblemHighlightType.LIKE_DEPRECATED)
        }
    }

    companion object {
        /** RouterOS v6 properties with a direct v7 replacement, keyed by `menu:property`. */
        val V6_REPLACEMENTS = mapOf(
            "ip/route:routing-mark" to "routing-table",
            "ipv6/route:routing-mark" to "routing-table",
        )
    }
}

class RosMissingMandatoryPropertyInspection : RosInspectionBase() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        if (element !is RosCommand) return
        val schema = RosSchemaService.schema(element.project) ?: return
        val target = RosSchemaContext.target(element, schema)
        if (!target.isKnown || target.verb != "add" || target.command != null) return
        val present = RosPsiUtil.namedArguments(element).map(RosPsiUtil::argumentName).toSet()
        if ("copy-from" in present) return
        if (RosPsiUtil.arguments(element).any { it !is RosNamedArgument }) return
        val missing = schema.argumentsOf(target).values.filter { it.mandatory && it.name !in present }.map { it.name }
        if (missing.isEmpty()) return
        val anchor = element.path ?: element
        holder.registerProblem(
            anchor, RosBundle.message("inspection.missing.mandatory", missing.joinToString(", ")), ProblemHighlightType.WEAK_WARNING,
        )
    }
}

/** Inside `where`, a property of the menu shadows a variable with the same name. */
class RosShadowedPropertyInspection : RosInspectionBase() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        if (element !is RosVariable) return
        if (PsiTreeUtil.getParentOfType(element, RosWhereClause::class.java) == null) return
        val command = PsiTreeUtil.getParentOfType(element, RosCommand::class.java) ?: return
        val schema = RosSchemaService.schema(element.project) ?: return
        val menu = RosSchemaContext.target(command, schema).menu ?: return
        val name = RosPsiUtil.variableName(element)
        if (name in menu.args || name in menu.readOnly) {
            holder.registerProblem(element, RosBundle.message("inspection.shadowed.property", name), ProblemHighlightType.WARNING)
        }
    }
}
