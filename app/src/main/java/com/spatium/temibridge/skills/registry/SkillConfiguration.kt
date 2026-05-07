package com.spatium.deamon.db.temi.skills.registry

import android.util.Log
import com.spatium.deamon.db.temi.skills.base.SkillCategory

data class SkillMetadata(
    val skillId: String,
    val name: String,
    val description: String,
    val category: SkillCategory,
    val parameters: List<ParameterSpec> = emptyList(),
    val requiredPermissions: List<String> = emptyList(),
    val timeoutMillis: Long = 30_000L,
    val retryable: Boolean = false,
    val enabled: Boolean = true,
)

data class ParameterSpec(
    val name: String,
    val type: ParameterType,
    val required: Boolean,
    val description: String,
    val defaultValue: Any? = null,
)

enum class ParameterType {
    STRING,
    NUMBER,
    BOOLEAN,
    LIST,
    MAP,
}

object SkillConfiguration {

    private const val TAG = "SkillConfiguration"

    private val configurations = mutableMapOf<String, SkillMetadata>()

    fun register(metadata: SkillMetadata) {
        configurations[metadata.skillId] = metadata
        Log.d(TAG, "Metadata registrada para skill: ${metadata.skillId}")
    }

    fun getMetadata(skillId: String): SkillMetadata? = configurations[skillId]

    fun getAllMetadata(): List<SkillMetadata> = configurations.values.toList()

    fun getSkillsByCategory(category: SkillCategory): List<SkillMetadata> =
        configurations.values.filter { it.category == category }

    fun isEnabled(skillId: String): Boolean =
        configurations[skillId]?.enabled ?: false

    fun getTimeout(skillId: String): Long =
        configurations[skillId]?.timeoutMillis ?: 30_000L

    fun isRetryable(skillId: String): Boolean =
        configurations[skillId]?.retryable ?: false

    fun clear() {
        configurations.clear()
        Log.d(TAG, "Configuraciones limpiadas")
    }
}
