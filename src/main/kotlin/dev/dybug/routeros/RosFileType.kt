package dev.dybug.routeros

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object RosFileType : LanguageFileType(RosLanguage) {
    override fun getName(): String = "RouterOS Script"

    override fun getDescription(): String = RosBundle.message("filetype.description")

    override fun getDefaultExtension(): String = "rsc"

    override fun getIcon(): Icon = RosIcons.FILE
}
