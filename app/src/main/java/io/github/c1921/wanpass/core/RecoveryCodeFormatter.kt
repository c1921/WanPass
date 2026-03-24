package io.github.c1921.wanpass.core

import java.security.SecureRandom

object RecoveryCodeFormatter {
    private const val Alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val GroupCount = 6
    private const val GroupLength = 4

    fun generate(random: SecureRandom = SecureRandom()): String {
        val raw = buildString(GroupCount * GroupLength) {
            repeat(GroupCount * GroupLength) {
                append(Alphabet[random.nextInt(Alphabet.length)])
            }
        }
        return display(raw)
    }

    fun normalize(input: String): String = input.uppercase().replace("-", "").replace(" ", "")

    fun display(input: String): String {
        val normalized = normalize(input)
        return normalized.chunked(GroupLength).joinToString(separator = "-")
    }
}
