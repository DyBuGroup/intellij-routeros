package dev.dybug.routeros.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import dev.dybug.routeros.RosLanguage

object RosElementFactory {
    fun file(project: Project, text: String): RosFile =
        PsiFileFactory.getInstance(project).createFileFromText("dummy.rsc", RosLanguage, text) as RosFile

    /** A name usable after `:local`: a bare word for letters/digits, otherwise a quoted string. */
    fun declarationName(project: Project, name: String): PsiElement {
        val decl = PsiTreeUtil.findChildOfType(file(project, ":local ${nameText(name)}"), RosLocalDecl::class.java)
        return RosPsiUtil.declaredNameElement(decl ?: error("cannot create declaration")) ?: error("cannot create name for '$name'")
    }

    fun variable(project: Project, name: String): RosVariable =
        PsiTreeUtil.findChildOfType(file(project, ":put \$${nameText(name)}"), RosVariable::class.java)
            ?: error("cannot create variable '$name'")

    fun statement(project: Project, text: String): PsiElement =
        file(project, text).firstChild ?: error("cannot create statement")

    fun nameText(name: String): String =
        if (isPlainName(name)) name else "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""

    fun isPlainName(name: String): Boolean = name.isNotEmpty() && name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
}
