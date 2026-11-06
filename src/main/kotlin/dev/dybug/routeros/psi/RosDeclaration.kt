package dev.dybug.routeros.psi

import com.intellij.psi.PsiNameIdentifierOwner

/** `:local` / `:global` declarations, including functions (`:local f do={…}`). */
interface RosDeclaration : PsiNameIdentifierOwner {
    val isGlobal: Boolean
    val isFunction: Boolean
}
