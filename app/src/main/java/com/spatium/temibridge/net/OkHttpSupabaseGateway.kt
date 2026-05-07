package com.spatium.deamon.db.temi.net

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OkHttpSupabaseGateway(
    private val baseUrl: String,
    private val anonKey: String,
) : SupabaseGateway {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun post(path: String, body: JsonObject): JsonElement {
        val url = "$baseUrl/$path".trimEnd('/')
        val requestBody = Json.encodeToString(JsonObject.serializer(), body)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $anonKey")
            .addHeader("apikey", anonKey)
            .build()

        return executeRequest(request)
    }

    override suspend fun get(path: String, query: Map<String, String>): JsonElement {
        val httpUrl = "$baseUrl/$path".trimEnd('/').toHttpUrl().newBuilder().apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

        val request = Request.Builder()
            .url(httpUrl)
            .get()
            .addHeader("Authorization", "Bearer $anonKey")
            .addHeader("apikey", anonKey)
            .build()

        return executeRequest(request)
    }

    private suspend fun executeRequest(request: Request): JsonElement =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val bodyStr = it.body?.string() ?: ""
                        if (!it.isSuccessful) {
                            cont.resumeWithException(
                                IllegalStateException("HTTP ${it.code}: $bodyStr"),
                            )
                        } else {
                            cont.resume(json.parseToJsonElement(bodyStr))
                        }
                    }
                }
            })
        }
}
