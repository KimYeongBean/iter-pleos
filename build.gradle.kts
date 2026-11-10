plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.aboutlibraries) apply false
}

// Module boundaries from docs/interfaces.md. Change both together (interfaces.md needs agreement first).
val allowedProjectDependencies = mapOf(
    ":app" to setOf(":vehicle", ":art", ":model"),
    ":vehicle" to setOf(":model"),
    ":art" to setOf(":model"),
    ":model" to emptySet(),
)
// Pure Kotlin/JVM modules: no Android plugin, no Pleos SDK.
val pureJvmModules = setOf(":art", ":model")
val forbiddenGroupsInPureJvm = setOf("ai.pleos.playground")

val boundaryViolations = mutableListOf<String>()

gradle.projectsEvaluated {
    subprojects.forEach { p ->
        val allowed = allowedProjectDependencies[p.path]
        if (allowed == null) {
            boundaryViolations += "${p.path}: not listed in allowedProjectDependencies (root build.gradle.kts)"
            return@forEach
        }
        p.configurations.forEach { conf ->
            conf.dependencies.forEach { dep ->
                if (dep is ProjectDependency && dep.path != p.path && dep.path !in allowed) {
                    boundaryViolations += "${p.path} -> ${dep.path} (configuration '${conf.name}')"
                }
                if (p.path in pureJvmModules && dep.group in forbiddenGroupsInPureJvm) {
                    boundaryViolations += "${p.path} -> ${dep.group}:${dep.name} (pure JVM module)"
                }
            }
        }
        if (p.path in pureJvmModules && p.plugins.hasPlugin("com.android.base")) {
            boundaryViolations += "${p.path}: applies an Android plugin (pure JVM module)"
        }
    }
}

val checkModuleBoundaries by tasks.registering {
    group = "verification"
    description = "Fails if module dependencies break docs/interfaces.md."
    doLast {
        if (boundaryViolations.isNotEmpty()) {
            throw GradleException(
                "Module boundary violations (see docs/interfaces.md):\n" +
                    boundaryViolations.joinToString("\n") { "  - $it" },
            )
        }
    }
}

// Formatting is a mechanical rule: ktlintCheck runs under check (./gradlew build, CI).
// Fix locally with ./gradlew ktlintFormat. Judgement rules live in CODING_STANDARDS.md.
subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(rootProject.libs.versions.ktlint.get())
    }
    plugins.withId("base") {
        tasks.named("check") { dependsOn(checkModuleBoundaries) }
    }
}
