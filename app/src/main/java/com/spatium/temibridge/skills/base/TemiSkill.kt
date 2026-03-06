package com.spatium.deamon.db.temi.skills.base

import android.content.Context

interface TemiSkill {
    val skillId: String
    val skillName: String
    val description: String
    val category: SkillCategory

    suspend fun execute(context: Context, params: Map<String, Any> = emptyMap()): SkillResult
    suspend fun canExecute(context: Context): Boolean
    fun getRequiredPermissions(): List<String>
}

enum class SkillCategory {
    NAVIGATION,
    SPEECH,
    SEQUENCE,
    TOUR,
    INTERACTION,
    COMPOSITE,
    SYSTEM,
    CUSTOM
}
