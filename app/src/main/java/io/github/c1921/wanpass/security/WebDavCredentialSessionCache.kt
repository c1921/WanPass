package io.github.c1921.wanpass.security

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavCredentialSessionCache @Inject constructor() {
    @Volatile
    private var cachedPassword: String? = null

    fun load(): String? = cachedPassword

    fun store(password: String) {
        cachedPassword = password
    }

    fun clear() {
        cachedPassword = null
    }
}
