package dev.dybug.routeros.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import dev.dybug.routeros.schema.RosSchemaService
import dev.dybug.routeros.schema.SchemaNode

/** Semantic helpers over the generated PSI. Menu paths are lists of segments without slashes. */
object RosPsiUtil {
    private val PATH_SEGMENTS = TokenSet.create(RosTypes.PATH_WORD)

    fun pathSegments(path: RosPath): List<String> =
        path.node.getChildren(PATH_SEGMENTS).map { it.text }

    fun isAbsolute(path: RosPath): Boolean = path.firstChild?.node?.elementType == RosTypes.SLASH

    /** Arguments of a command: named arguments, where clauses and positional values. */
    fun arguments(command: PsiElement): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        var child = command.firstChild
        var first = true
        while (child != null) {
            val skip = child is RosPath || child is PsiWhiteSpace || child is PsiComment || child is PsiErrorElement ||
                (first && child.node.elementType in RosTokenSets.SCRIPT_COMMANDS)
            if (!skip) result += child
            if (child !is PsiWhiteSpace && child !is PsiComment) first = false
            child = child.nextSibling
        }
        return result
    }

    fun namedArguments(command: PsiElement): List<RosNamedArgument> =
        PsiTreeUtil.getChildrenOfTypeAsList(command, RosNamedArgument::class.java)

    fun argumentName(argument: RosNamedArgument): String {
        val key = argument.firstChild ?: return ""
        return if (key is RosString) stringValue(key) else key.text
    }

    fun argumentValue(argument: RosNamedArgument): PsiElement? {
        var child = argument.firstChild?.nextSibling
        while (child != null && child.node.elementType != RosTypes.EQ) child = child.nextSibling
        child = child?.nextSibling
        while (child != null && child.node.elementType == com.intellij.psi.TokenType.WHITE_SPACE) child = child.nextSibling
        return child
    }

    fun argumentValueText(argument: RosNamedArgument): String? {
        val value = argumentValue(argument) ?: return null
        return if (value is RosString) stringValue(value) else value.text
    }

    fun namedArgument(command: PsiElement, name: String): RosNamedArgument? =
        namedArguments(command).firstOrNull { argumentName(it) == name }

    /** The text of a string literal without quotes; escapes are kept as written. */
    fun stringValue(string: RosString): String = contentRange(string).substring(string.text)

    fun contentRange(string: RosString): TextRange {
        val first = string.firstChild
        val last = string.lastChild
        val start = if (first?.node?.elementType == RosTypes.STRING_START) first.textLength else 0
        val end = if (last != first && last?.node?.elementType == RosTypes.STRING_END) string.textLength - last.textLength else string.textLength
        return TextRange(start, maxOf(start, end))
    }

    /** Name declared by `:local`/`:global`, unquoted. */
    fun declaredNameElement(decl: PsiElement): PsiElement? {
        val keyword = decl.firstChild ?: return null
        var next = keyword.nextSibling
        while (next != null && next.node.elementType == com.intellij.psi.TokenType.WHITE_SPACE) next = next.nextSibling
        return next?.takeIf { it.node.elementType == RosTypes.WORD || it is RosString }
    }

    /** Canonical (escape-decoded) name, so that `"a\62"` and `ab` are the same variable. */
    fun declaredName(decl: PsiElement): String? = when (val element = declaredNameElement(decl)) {
        null -> null
        is RosString -> RosEscapes.decode(stringValue(element))
        else -> element.text
    }

    /** `:local f do={…}` / `:global f do={…}` declares a function. */
    fun isFunctionDeclaration(decl: PsiElement): Boolean =
        namedArguments(decl).any { argumentName(it) == "do" && argumentValue(it) is RosBlock }

    /** Variable name referenced by `$name` / `$"name"`, unquoted. */
    fun variableName(variable: RosVariable): String {
        val first = variable.firstChild ?: return ""
        return when (first.node.elementType) {
            RosTypes.DOLLAR_NAME -> first.text.removePrefix("$")
            else -> PsiTreeUtil.getChildOfType(variable, RosString::class.java)?.let { RosEscapes.decode(stringValue(it)) } ?: ""
        }
    }

    // ------------------------------------------------------------------ menu context

    /**
     * A bare absolute path on its own line at top level (`/ip address`) switches the current menu
     * for following relative commands, as in `/export` output.
     */
    fun isMenuContextLine(command: RosCommand): Boolean {
        val path = command.path ?: return false
        if (!isAbsolute(path)) return false
        if (command.parent !is RosFile) return false
        if (arguments(command).isNotEmpty()) return false
        // `/ip address print` and `/system reboot` invoke commands; `/ip address` selects a menu
        val schema = RosSchemaService.schema(command.project) ?: return true
        val segments = resolve(emptyList(), pathSegments(path))
        val node = schema.node(segments)
        if (node != null) return node.kind != SchemaNode.Kind.COMMAND
        val target = schema.resolveCommand(segments)
        // an unknown word followed by more words is most likely a command with positional arguments
        return target.verb == null && target.consumed >= segments.size - 1
    }

    /** A top-level command that continues an export section: relative path or no path at all. */
    fun isRelativeCommand(element: PsiElement): Boolean {
        if (element !is RosCommand) return false
        val path = element.path ?: return true
        return !isAbsolute(path)
    }

    /** Absolute menu path that applies to [element], from the nearest preceding context line. */
    fun menuContextAt(element: PsiElement): List<String> {
        val file = element.containingFile as? RosFile ?: return emptyList()
        val offset = element.textRange.startOffset
        val contexts = contextLines(file)
        var low = 0
        var high = contexts.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (contexts[mid].first < offset) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return if (found >= 0) contexts[found].second else emptyList()
    }

    private fun contextLines(file: RosFile): List<Pair<Int, List<String>>> =
        CachedValuesManager.getCachedValue(file) {
            val result = mutableListOf<Pair<Int, List<String>>>()
            var current = emptyList<String>()
            for (child in file.children) {
                if (child is RosCommand && isMenuContextLine(child)) {
                    current = resolve(emptyList(), pathSegments(child.path!!))
                    result += child.textRange.startOffset to current
                }
            }
            CachedValueProvider.Result.create(result.toList(), file)
        }

    /**
     * Absolute segments of the command's own path (menus plus the command word), resolved against
     * the menu context for relative paths. Context lines resolve to the menu they select.
     */
    fun resolvedPath(command: RosCommand): List<String> {
        val path = command.path ?: return menuContextAt(command)
        val segments = pathSegments(path)
        return if (isAbsolute(path)) resolve(emptyList(), segments) else resolve(menuContextAt(command), segments)
    }

    private fun resolve(base: List<String>, segments: List<String>): List<String> {
        val result = base.toMutableList()
        for (segment in segments) {
            if (segment == "..") {
                if (result.isNotEmpty()) result.removeAt(result.size - 1)
            } else {
                result += segment
            }
        }
        return result
    }
}
