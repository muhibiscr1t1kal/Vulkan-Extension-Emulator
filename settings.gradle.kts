pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://repo.codemc.io/repository/relativitymc/")
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}