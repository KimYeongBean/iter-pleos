// #124 실차 확인용 임시 앱. main에 merge하지 않는다.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.github.kimyeongbean.iter.probe"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    useLibrary("android.car")

    defaultConfig {
        // Separate package, so it installs next to the real app.
        applicationId = "io.github.kimyeongbean.iter.probe"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidCompileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-probe"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.pleos.vehicle)
}
