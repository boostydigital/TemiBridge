package com.spatium.deamon.db.temi.skills.registry

import android.util.Log
import com.spatium.deamon.db.temi.skills.base.SkillCategory
import com.spatium.deamon.db.temi.skills.base.TemiSkill

object SkillRegistry {

    private const val TAG = "SkillRegistry"

    private val skills = mutableMapOf<String, TemiSkill>()

    fun register(skill: TemiSkill) {
        skills[skill.skillId] = skill
        Log.d(TAG, "Skill registrado: id=${skill.skillId} name=${skill.skillName} category=${skill.category}")
    }

    fun registerAll(vararg skillList: TemiSkill) {
        skillList.forEach { register(it) }
    }

    fun getSkill(skillId: String): TemiSkill? {
        val skill = skills[skillId]
        if (skill == null) Log.w(TAG, "Skill no encontrado: $skillId")
        return skill
    }

    fun getAllSkills(): List<TemiSkill> = skills.values.toList()

    fun getSkillsByCategory(category: SkillCategory): List<TemiSkill> =
        skills.values.filter { it.category == category }

    fun getSkillsByPermission(permission: String): List<TemiSkill> =
        skills.values.filter { permission in it.getRequiredPermissions() }

    fun isRegistered(skillId: String): Boolean = skills.containsKey(skillId)

    fun unregister(skillId: String) {
        val removed = skills.remove(skillId)
        if (removed != null) {
            Log.d(TAG, "Skill desregistrado: $skillId")
        }
    }

    fun clear() {
        skills.clear()
        Log.d(TAG, "Registry limpiado")
    }

    fun logAllSkills() {
        Log.d(TAG, "=== Skills registrados (${skills.size}) ===")
        skills.values.forEach { skill ->
            Log.d(TAG, "  [${skill.category}] ${skill.skillId} -> ${skill.skillName}")
        }
        Log.d(TAG, "==============================================")
    }
}
