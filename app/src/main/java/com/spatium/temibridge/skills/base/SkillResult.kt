package com.spatium.deamon.db.temi.skills.base

sealed class SkillResult {
    object Success : SkillResult()
    data class PartialSuccess(val message: String) : SkillResult()
    data class Error(val message: String, val cause: Throwable? = null) : SkillResult()

    val isSuccess: Boolean get() = this is Success || this is PartialSuccess
    val isError: Boolean get() = this is Error
}
