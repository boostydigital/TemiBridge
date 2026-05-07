// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.spotless)
}

// Non-auto-fixable rules disabled for existing codebase.
// Enforce on new code via PR review. Re-enable gradually as files are cleaned up.
val ktlintDisabledRules = mapOf(
    "ktlint_standard_max-line-length" to "disabled",
    "ktlint_standard_no-wildcard-imports" to "disabled",
    "ktlint_standard_property-naming" to "disabled",
    "ktlint_standard_function-naming" to "disabled",
    "ktlint_standard_value-parameter-comment" to "disabled",
    "ktlint_standard_no-unused-imports" to "disabled",
    "ktlint_standard_blank-line-between-declarations" to "disabled",
)

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintDisabledRules)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**/*.gradle.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintDisabledRules)
    }
}
