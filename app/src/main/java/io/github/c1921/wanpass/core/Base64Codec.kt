package io.github.c1921.wanpass.core

import android.util.Base64

object Base64Codec {
    fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
