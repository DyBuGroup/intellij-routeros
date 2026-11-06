package dev.dybug.routeros.completion

import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import dev.dybug.routeros.psi.RosCommand
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosNamedArgument
import dev.dybug.routeros.psi.RosPsiUtil

/** Values that appear in the file, used to complete names that the router knows only at runtime. */
object RosProjectValues {
    private const val MAX_VALUES = 200

    /** Interface names defined in the file: `name=` / `default-name=` under /interface, plus common built-ins. */
    fun interfaceNames(file: RosFile): Set<String> = CachedValuesManager.getCachedValue(file) {
        val names = linkedSetOf<String>()
        for (command in PsiTreeUtil.findChildrenOfType(file, RosCommand::class.java)) {
            val path = RosPsiUtil.resolvedPath(command)
            if (path.firstOrNull() != "interface") continue
            for (key in listOf("name", "default-name")) {
                RosPsiUtil.namedArgument(command, key)?.let(RosPsiUtil::argumentValueText)?.let { names += it }
            }
            if (names.size > MAX_VALUES) break
        }
        CachedValueProvider.Result.create(names + listOf("all", "none"), file)
    }

    /** Distinct literal values used for an argument name anywhere in the file. */
    fun valuesUsedFor(file: RosFile, name: String): Set<String> {
        val byName = CachedValuesManager.getCachedValue(file) {
            val map = mutableMapOf<String, MutableSet<String>>()
            for (argument in PsiTreeUtil.findChildrenOfType(file, RosNamedArgument::class.java)) {
                val value = RosPsiUtil.argumentValueText(argument) ?: continue
                if (value.isEmpty() || value.length > 64 || value.startsWith("$")) continue
                val set = map.getOrPut(RosPsiUtil.argumentName(argument)) { linkedSetOf() }
                if (set.size < MAX_VALUES) set += value
            }
            CachedValueProvider.Result.create(map as Map<String, Set<String>>, file)
        }
        return byName[name].orEmpty()
    }
}
