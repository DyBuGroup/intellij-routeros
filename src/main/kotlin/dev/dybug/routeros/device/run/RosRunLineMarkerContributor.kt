package dev.dybug.routeros.device.run

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import dev.dybug.routeros.RosBundle
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosTypes

/** A run icon on the first statement of a RouterOS file. */
class RosRunLineMarkerContributor : RunLineMarkerContributor() {
    override fun getInfo(element: PsiElement): Info? {
        if (element.firstChild != null) return null
        if (element is PsiWhiteSpace || element is PsiComment || element.node.elementType == RosTypes.NEWLINE) return null
        val file = element.containingFile as? RosFile ?: return null
        if (file.virtualFile?.isInLocalFileSystem != true) return null
        if (firstCodeLeaf(file) !== element) return null
        return Info(AllIcons.RunConfigurations.TestState.Run, ExecutorAction.getActions(0)) { RosBundle.message("run.gutter.tooltip") }
    }

    /** Computed once per file version, not once per leaf. */
    private fun firstCodeLeaf(file: RosFile): PsiElement? = com.intellij.psi.util.CachedValuesManager.getCachedValue(file) {
        var current: PsiElement? = PsiTreeUtil.firstChild(file)
        while (current != null && (current is PsiWhiteSpace || current is PsiComment || current.node.elementType == RosTypes.NEWLINE)) {
            current = PsiTreeUtil.nextLeaf(current)
        }
        com.intellij.psi.util.CachedValueProvider.Result.create(current, file)
    }
}
