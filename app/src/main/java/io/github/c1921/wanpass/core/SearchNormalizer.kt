package io.github.c1921.wanpass.core

import java.util.Locale

object SearchNormalizer {
    fun normalize(input: String): String = input
        .trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)

    fun buildSearchBlob(parts: List<String>): String = normalize(parts.joinToString(separator = "\n") { it.trim() })
}
