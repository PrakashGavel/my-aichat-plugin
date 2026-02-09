package com.github.prakashgavel.myaichatplugin.commit

import org.junit.Assert.assertTrue
import org.junit.Test

class DiffSummarizerTest {
    @Test
    fun `summarize simple diff`() {
        val diff = """
            diff --git a/src/Foo.kt b/src/Foo.kt
            index e69de29..4b825dc 100644
            --- a/src/Foo.kt
            +++ b/src/Foo.kt
            @@ -0,0 +1,3 @@
            +package a
            +class Foo {}
            +
        """.trimIndent()
        val res = DiffSummarizer().summarize(diff, "feature/PROJ-123-add-foo")
        assertTrue(res.subject.contains("feat:"))
        assertTrue(res.subject.contains("[PROJ-123]"))
        assertTrue(res.body.contains("Files changed: 1"))
    }
}

