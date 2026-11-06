package dev.dybug.routeros.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.psi.RosBlock
import dev.dybug.routeros.psi.RosCommand
import dev.dybug.routeros.psi.RosDeclaration
import dev.dybug.routeros.psi.RosNamedArgument
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosSetStatement
import dev.dybug.routeros.psi.RosVariable
import dev.dybug.routeros.references.RosSetTargetReference
import dev.dybug.routeros.references.RosVariableReference

/**
 * `$x` without a declaration in scope. If `x` is a `:global` declared in another project file,
 * the classic gotcha applies: a script must declare `:global x` itself to see it.
 */
class RosUndefinedVariableInspection : RosInspectionBase() {
    override fun visit(element: PsiElement, holder: ProblemsHolder) {
        if (element is RosSetStatement) {
            visitSet(element, holder)
            return
        }
        if (element !is RosVariable) return
        val reference = element.reference as? RosVariableReference ?: return
        val name = reference.variableName
        if (name.isEmpty() || name.all(Char::isDigit) || name in HOOK_VARIABLES) return
        if (reference.resolveLocally() != null) return
        val range = reference.rangeInElement
        val globals = reference.multiResolve(false).mapNotNull { it.element as? RosDeclaration }.filter { it.isGlobal }
        if (globals.isNotEmpty()) {
            holder.registerProblem(
                element, RosBundle.message("inspection.global.not.declared", name), ProblemHighlightType.WARNING, range,
                RosQuickFixes.Declare(name, global = true),
            )
            return
        }
        if (isCallerProvided(element)) return
        holder.registerProblem(
            element, RosBundle.message("inspection.undefined.variable", name), ProblemHighlightType.WEAK_WARNING, range,
            RosQuickFixes.Declare(name, global = false), RosQuickFixes.Declare(name, global = true),
        )
    }

    /** `:set x` on an undeclared variable is a compile error on the router. */
    private fun visitSet(statement: RosSetStatement, holder: ProblemsHolder) {
        val reference = statement.reference as? RosSetTargetReference ?: return
        if (reference.resolveLocally() != null) return
        val name = reference.variableName
        val global = reference.multiResolve(false).isNotEmpty()
        if (!global && isCallerProvided(statement)) return
        holder.registerProblem(
            statement,
            RosBundle.message(if (global) "inspection.global.not.declared" else "inspection.set.undeclared", name),
            ProblemHighlightType.WARNING, reference.rangeInElement,
            RosQuickFixes.Declare(name, global = global),
        )
    }

    /** Function parameters (`$f a=1` → `$a`) and variables that menu commands pass to `do={…}` blocks. */
    private fun isCallerProvided(variable: PsiElement): Boolean {
        var block = PsiTreeUtil.getParentOfType(variable, RosBlock::class.java)
        while (block != null) {
            val argument = block.parent as? RosNamedArgument
            val owner = argument?.parent
            if (argument != null && RosPsiUtil.argumentName(argument) == "do" && (owner is RosDeclaration || owner is RosCommand)) return true
            block = PsiTreeUtil.getParentOfType(block, RosBlock::class.java)
        }
        return false
    }

    companion object {
        /** Variables that RouterOS provides to hook scripts (DHCP, PPP, netwatch, hotspot…). */
        val HOOK_VARIABLES = setOf(
            "leaseBound", "leaseServerName", "leaseActMAC", "leaseActIP", "lease-hostname", "lease-options",
            "bound", "server-address", "interface", "lease-address", "gateway-address", "vendor-specific",
            "user", "local-address", "remote-address", "caller-id", "called-id", "service",
            "mac-address", "address", "host", "status", "since", "done-tests", "failed-tests",
            "pppoe", "username", "hostname", "pool",
        )
    }
}
