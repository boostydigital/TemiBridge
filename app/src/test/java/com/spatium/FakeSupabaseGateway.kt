package com.spatium

import com.spatium.deamon.db.temi.net.SupabaseGateway
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.util.LinkedList

class FakeSupabaseGateway : SupabaseGateway {
    data class Call(
        val method: String,
        val path: String,
        val body: JsonObject? = null,
        val query: Map<String, String> = emptyMap(),
    )

    val calls = mutableListOf<Call>()
    private val responseQueue = LinkedList<JsonElement>()

    fun enqueue(response: JsonElement) {
        responseQueue.add(response)
    }

    override suspend fun post(path: String, body: JsonObject): JsonElement {
        calls.add(Call("POST", path, body))
        return responseQueue.poll() ?: buildJsonObject {}
    }

    override suspend fun get(path: String, query: Map<String, String>): JsonElement {
        calls.add(Call("GET", path, query = query))
        return responseQueue.poll() ?: buildJsonObject {}
    }
}
