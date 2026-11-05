package dev.dybug.routeros.editor

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import dev.dybug.routeros.psi.RosTypes

class RosQuoteHandler : SimpleTokenSetQuoteHandler(RosTypes.STRING_START, RosTypes.STRING_END)
