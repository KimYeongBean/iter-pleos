// Drawing logic. Pure Kotlin/JVM: must not depend on Android or the Pleos SDK.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    // Every declaration states its visibility; public is for outputs in docs/interfaces.md only.
    explicitApi()
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    // Fake drive data returns :model drive sessions.
    api(project(":model"))
    testImplementation(libs.junit)
}
