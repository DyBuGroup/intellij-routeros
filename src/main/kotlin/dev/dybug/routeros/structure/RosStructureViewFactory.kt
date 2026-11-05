package dev.dybug.routeros.structure

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosGlobalDecl
import dev.dybug.routeros.psi.RosLocalDecl

class RosStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (psiFile !is RosFile) return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel = RosStructureViewModel(psiFile, editor)

            override fun isRootNodeShown(): Boolean = false
        }
    }
}

class RosStructureViewModel(file: RosFile, editor: Editor?) :
    StructureViewModelBase(file, editor, RosStructureElement.FileElement(file)),
    StructureViewModel.ElementInfoProvider {
    init {
        withSuitableClasses(RosLocalDecl::class.java, RosGlobalDecl::class.java, dev.dybug.routeros.psi.RosCommand::class.java)
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean =
        element is RosStructureElement.CommandElement || element is RosStructureElement.DeclarationElement && !element.isFunction
}
