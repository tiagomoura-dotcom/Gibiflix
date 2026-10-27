pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // SDK da Onyx (refresh e-ink dos Boox). Só HTTP; restrito ao grupo da Onyx.
        maven {
            url = uri("http://repo.boox.com/repository/maven-public/")
            isAllowInsecureProtocol = true
            content { includeGroupByRegex("com\\.onyx.*") }
        }
    }
}

rootProject.name = "Gibiflix"

include(":core")

// O :app só entra quando há Android SDK. Assim `./gradlew :core:test` roda em qualquer JDK.
val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    System.getenv("ANDROID_SDK_ROOT") != null ||
    file("local.properties").let { it.exists() && it.readText().contains("sdk.dir") }
if (hasAndroidSdk) {
    include(":app")
} else {
    logger.lifecycle("Android SDK não encontrado: só o módulo :core será configurado.")
}
