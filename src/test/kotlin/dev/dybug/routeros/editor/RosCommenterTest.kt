package dev.dybug.routeros.editor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RosCommenterTest : BasePlatformTestCase() {
    private fun toggle(text: String): String {
        myFixture.configureByText("test.rsc", text)
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        return myFixture.editor.document.text
    }

    fun testCommentAddsSpace() {
        assertEquals("# add address=1\n", toggle("<caret>add address=1\n"))
    }

    fun testUncommentRemovesSpace() {
        assertEquals("add address=1\n", toggle("<caret># add address=1\n"))
    }

    fun testUncommentWithoutSpace() {
        assertEquals("add address=1\n", toggle("<caret>#add address=1\n"))
    }

    fun testRoundTripOverSelection() {
        myFixture.configureByText("test.rsc", "<selection>add a=1\nadd b=2\n</selection>")
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        assertEquals("# add a=1\n# add b=2\n", myFixture.editor.document.text)
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
        assertEquals("add a=1\nadd b=2\n", myFixture.editor.document.text)
    }
}
