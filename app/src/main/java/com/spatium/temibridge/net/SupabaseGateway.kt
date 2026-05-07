package com.spatium.deamon.db.temi.net

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

interface SupabaseGateway {
    suspend fun post(path: String, body: JsonObject): JsonElement
    suspend fun get(path: String, query: Map<String, String> = emptyMap()): JsonElement
}
