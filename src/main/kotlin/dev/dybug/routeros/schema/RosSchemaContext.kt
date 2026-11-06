package dev.dybug.routeros.schema

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import dev.dybug.routeros.psi.RosCommand
import dev.dybug.routeros.psi.RosGlobalDecl
import dev.dybug.routeros.psi.RosLocalDecl
import dev.dybug.routeros.psi.RosPsiUtil
import dev.dybug.routeros.psi.RosScriptCommand
import dev.dybug.routeros.psi.RosSetStatement

/** Connects PSI to the schema: which menu/command a command line targets, which script command a line uses. */
object RosSchemaContext {
    fun target(command: RosCommand, schema: RosSchema): RosSchema.CommandTarget =
        schema.resolveCommand(RosPsiUtil.resolvedPath(command))

    /** The script command (`:foreach` → foreach) of a script command statement, if known. */
    fun scriptCommand(statement: PsiElement, schema: RosSchema): ScriptCommand? {
        val name = scriptCommandName(statement) ?: return null
        return schema.builtins.scriptCommands[name]
    }

    fun scriptCommandName(statement: PsiElement): String? = when (statement) {
        is RosScriptCommand, is RosLocalDecl, is RosGlobalDecl, is RosSetStatement ->
            statement.firstChild?.text?.removePrefix(":")
        else -> null
    }

    /** Arguments accepted by the command or script command that owns [element]. */
    fun argumentsFor(owner: PsiElement, schema: RosSchema): Map<String, SchemaArg> = when (owner) {
        is RosCommand -> schema.argumentsOf(target(owner, schema))
        else -> scriptCommand(owner, schema)?.params.orEmpty()
    }

    fun owningStatement(element: PsiElement): PsiElement? =
        PsiTreeUtil.getParentOfType(element, RosCommand::class.java, RosScriptCommand::class.java, RosLocalDecl::class.java, RosGlobalDecl::class.java, RosSetStatement::class.java)
}
