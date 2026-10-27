import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val ksFile: String? = System.getenv("GIBIFLIX_KEYSTORE_FILE")
val ksPass: String? = System.getenv("GIBIFLIX_KEYSTORE_PASSWORD")

android {
    namespace = "app.gibiflix"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.gibiflix"
        minSdk = 30
        targetSdk = 36
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "0.1.$run"
        ndk { abiFilters += "arm64-v8a" }
        vectorDrawables { useSupportLibrary = false }
    }

    signingConfigs {
        if (!ksFile.isNullOrBlank() && !ksPass.isNullOrBlank()) {
            create("release") {
                storeFile = file(ksFile)
                storePassword = ksPass
                keyAlias = "gibiflix"
                keyPassword = ksPass
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false // ligar só depois de regras ProGuard testadas
        }
    }

    splits { abi { isEnable = false } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/DEPENDENCIES",
            )
        }
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

ksp {
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.documentfile)
    implementation(libs.coil.compose)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    implementation(libs.libarchive)
    implementation(libs.hiddenapibypass)
    implementation(libs.mlkit.text)
    implementation(libs.mlkit.translate)
    // Chamado só por reflexão (OnyxEinkController).
    implementation(libs.onyx.device)

    testImplementation(libs.junit)
}
