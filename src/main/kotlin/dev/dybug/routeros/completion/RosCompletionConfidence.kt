package dev.dybug.routeros.completion

import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ThreeState

/** No completion popup while typing inside a `#` comment. */
class RosCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState =
        if (RosCompletionContributor.isInComment(contextElement)) ThreeState.YES else ThreeState.UNSURE
}
