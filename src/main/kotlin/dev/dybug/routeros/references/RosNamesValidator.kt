package dev.dybug.routeros.references

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project

/** Variable names: any non-empty name; names other than letters and digits are written quoted. */
class RosNamesValidator : NamesValidator {
    override fun isKeyword(name: String, project: Project?): Boolean = name in KEYWORDS

    override fun isIdentifier(name: String, project: Project?): Boolean =
        name.isNotEmpty() && name.none { it == '"' || it == '\n' || it == '\r' } && !isKeyword(name, project)

    companion object {
        val KEYWORDS = setOf("and", "or", "in")
    }
}
