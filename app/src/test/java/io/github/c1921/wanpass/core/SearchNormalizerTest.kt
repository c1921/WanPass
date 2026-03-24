package io.github.c1921.wanpass.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchNormalizerTest {
    @Test
    fun `normalize should trim collapse whitespace and lowercase`() {
        val normalized = SearchNormalizer.normalize("  Taobao   ACCOUNT  ")

        assertEquals("taobao account", normalized)
    }

    @Test
    fun `buildSearchBlob should join fields into normalized text`() {
        val blob = SearchNormalizer.buildSearchBlob(
            listOf(" 淘宝 ", "138xxxx1234", "Taobao App", "购物账号")
        )

        assertEquals("淘宝 138xxxx1234 taobao app 购物账号", blob)
    }
}
