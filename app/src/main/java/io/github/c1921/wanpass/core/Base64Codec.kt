package io.github.c1921.wanpass.core

import java.util.Base64

object Base64Codec {
    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
