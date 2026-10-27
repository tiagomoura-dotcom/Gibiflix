// Plugins no classpath da raiz (um só classloader para Kotlin, KSP e AGP).
// O AGP só é carregado quando há Android SDK, para o :core compilar sem ele.
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null ||
        file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.kotlin.serialization.gradle.plugin)
        if (hasAndroidSdk) {
            classpath(libs.android.gradle.plugin)
            classpath(libs.kotlin.compose.gradle.plugin)
            classpath(libs.ksp.gradle.plugin)
        }
    }
}
