package io.github.kdownloadfile

import io.github.kdownloadfile.internal.freeFileName
import kotlin.test.Test
import kotlin.test.assertEquals

class FreeFileNameTest {
    @Test
    fun keepsNameWhenFree() {
        assertEquals("report.pdf", freeFileName("report.pdf") { false })
    }

    @Test
    fun addsCounterBeforeExtension() {
        val taken = setOf("report.pdf", "report (1).pdf")
        assertEquals("report (2).pdf", freeFileName("report.pdf") { it in taken })
    }

    @Test
    fun handlesNamesWithoutExtension() {
        assertEquals("notes (1)", freeFileName("notes") { it == "notes" })
    }

    @Test
    fun treatsLeadingDotAsPartOfName() {
        assertEquals(".env (1)", freeFileName(".env") { it == ".env" })
    }
}
