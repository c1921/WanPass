package io.github.c1921.wanpass.data.webdav

import io.github.c1921.wanpass.domain.model.WebDavRuntimeConfig
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Singleton
class WebDavClient @Inject constructor() {
    private val httpClient = OkHttpClient()
    private val xmlMediaType = "application/xml; charset=utf-8".toMediaType()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun testConnection(config: WebDavRuntimeConfig) = withContext(Dispatchers.IO) {
        val request = requestBuilder(config, config.baseUrl.toHttpUrl())
            .header("Depth", "0")
            .method("PROPFIND", propfindBody())
            .build()
        execute(request).use { response ->
            if (!response.isSuccessful && response.code != 207) {
                throw IOException("WebDAV 连接失败（HTTP ${response.code}）")
            }
        }
    }

    suspend fun ensureCollections(config: WebDavRuntimeConfig) = withContext(Dispatchers.IO) {
        collectionUrls(config).forEach { url ->
            execute(
                requestBuilder(config, url)
                    .method("MKCOL", ByteArray(0).toRequestBody(null))
                    .build()
            ).use { response ->
                if (response.code !in setOf(200, 201, 301, 302, 405)) {
                    throw IOException("创建 WebDAV 目录失败（HTTP ${response.code}）")
                }
            }
        }
    }

    suspend fun getJsonIfExists(config: WebDavRuntimeConfig, url: HttpUrl): String? = withContext(Dispatchers.IO) {
        execute(requestBuilder(config, url).get().build()).use { response ->
            when (response.code) {
                200 -> response.body?.string().orEmpty()
                404 -> null
                else -> throw IOException("读取 WebDAV 文件失败（HTTP ${response.code}）")
            }
        }
    }

    suspend fun putJson(config: WebDavRuntimeConfig, url: HttpUrl, content: String) = withContext(Dispatchers.IO) {
        execute(
            requestBuilder(config, url)
                .put(content.toRequestBody(jsonMediaType))
                .build()
        ).use { response ->
            if (!response.isSuccessful) {
                throw IOException("写入 WebDAV 文件失败（HTTP ${response.code}）")
            }
        }
    }

    suspend fun deleteIfExists(config: WebDavRuntimeConfig, url: HttpUrl) = withContext(Dispatchers.IO) {
        execute(requestBuilder(config, url).delete().build()).use { response ->
            if (response.code !in setOf(200, 204, 404)) {
                throw IOException("删除 WebDAV 文件失败（HTTP ${response.code}）")
            }
        }
    }

    fun rootUrl(config: WebDavRuntimeConfig): HttpUrl {
        val builder = config.baseUrl.toHttpUrl().newBuilder()
        config.remoteRoot.split('/').filter { it.isNotBlank() }.forEach { builder.addPathSegment(it) }
        return builder.build()
    }

    fun childUrl(parent: HttpUrl, child: String): HttpUrl = parent.newBuilder().addPathSegment(child).build()

    private fun collectionUrls(config: WebDavRuntimeConfig): List<HttpUrl> {
        val base = config.baseUrl.toHttpUrl()
        val builder = base.newBuilder()
        val urls = mutableListOf<HttpUrl>()
        config.remoteRoot.split('/').filter { it.isNotBlank() }.forEach { segment ->
            builder.addPathSegment(segment)
            urls += builder.build()
        }
        urls += childUrl(rootUrl(config), "items")
        return urls
    }

    private fun requestBuilder(config: WebDavRuntimeConfig, url: HttpUrl): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", Credentials.basic(config.username, config.password))

    private fun propfindBody() = """
        <?xml version="1.0" encoding="utf-8" ?>
        <d:propfind xmlns:d="DAV:">
          <d:prop>
            <d:resourcetype />
          </d:prop>
        </d:propfind>
    """.trimIndent().toRequestBody(xmlMediaType)

    private fun execute(request: Request): Response = httpClient.newCall(request).execute()
}
