package dev.dybug.routeros.formatting

import com.intellij.psi.formatter.StaticSymbolWhiteSpaceDefinitionStrategy

/** A `\` line continuation sits between tokens; the formatter must accept it as part of a gap. */
class RosWhiteSpaceFormattingStrategy : StaticSymbolWhiteSpaceDefinitionStrategy('\\')
