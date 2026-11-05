package dev.dybug.routeros.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import dev.dybug.routeros.RosFileType
import dev.dybug.routeros.RosLanguage

class RosFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, RosLanguage) {
    override fun getFileType(): FileType = RosFileType

    override fun toString(): String = "RouterOS File"
}
