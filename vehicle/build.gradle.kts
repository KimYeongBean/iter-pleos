plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "io.github.kimyeongbean.iter.vehicle"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    // CarPropertyManager for vehicle speed (the Pleos Vehicle SDK has no vehicle speed).
    useLibrary("android.car")

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    // Every declaration states its visibility; public is for outputs in docs/interfaces.md only.
    explicitApi()
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    // Record store API exposes :model types to consumers.
    api(project(":model"))
    // Record store API exposes Flow.
    api(libs.kotlinx.coroutines.core)
    implementation(libs.pleos.vehicle)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
