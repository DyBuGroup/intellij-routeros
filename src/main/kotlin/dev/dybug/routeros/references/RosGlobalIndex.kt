package dev.dybug.routeros.references

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import dev.dybug.routeros.RosFileType
import dev.dybug.routeros.psi.RosDeclaration
import dev.dybug.routeros.psi.RosFile
import dev.dybug.routeros.psi.RosGlobalDecl

/** Names declared with `:global` → files, for cross-file resolve and Go to Symbol. */
class RosGlobalIndex : ScalarIndexExtension<String>() {
    override fun getName(): ID<String, Void> = NAME

    override fun getIndexer(): DataIndexer<String, Void, FileContent> = DataIndexer { content ->
        val file = content.psiFile as? RosFile ?: return@DataIndexer emptyMap()
        PsiTreeUtil.findChildrenOfType(file, RosGlobalDecl::class.java)
            .mapNotNull { (it as RosDeclaration).name }
            .filter { it.isNotEmpty() && it.length < 256 }
            .associateWith { null }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getVersion(): Int = 2

    override fun getInputFilter(): FileBasedIndex.InputFilter = DefaultFileTypeSpecificInputFilter(RosFileType)

    override fun dependsOnFileContent(): Boolean = true

    companion object {
        val NAME: ID<String, Void> = ID.create("dev.dybug.routeros.globals")

        /** Global declarations with [name] in [scope] (project files by default). */
        fun declarations(project: Project, name: String, scope: GlobalSearchScope = GlobalSearchScope.projectScope(project)): List<RosDeclaration> {
            val manager = PsiManager.getInstance(project)
            return FileBasedIndex.getInstance().getContainingFiles(NAME, name, scope)
                .mapNotNull { manager.findFile(it) as? RosFile }
                .flatMap { file -> PsiTreeUtil.findChildrenOfType(file, RosGlobalDecl::class.java) }
                .map { it as RosDeclaration }
                .filter { it.name == name }
        }

        fun allNames(project: Project): Collection<String> = FileBasedIndex.getInstance().getAllKeys(NAME, project)
    }
}
