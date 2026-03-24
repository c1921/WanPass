package io.github.c1921.wanpass.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeFormatterTest {
    @Test
    fun `generate should return grouped display code`() {
        val code = RecoveryCodeFormatter.generate()

        assertTrue(code.matches(Regex("[A-Z2-9]{4}(-[A-Z2-9]{4}){5}")))
    }

    @Test
    fun `normalize should ignore case spaces and dashes`() {
        val normalized = RecoveryCodeFormatter.normalize("ab12-cd34 ef56")

        assertEquals("AB12CD34EF56", normalized)
    }
}
