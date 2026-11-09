import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    `java-library`
    `maven-publish`
    id("org.relativitymc.neo-loom") version "1.18-SNAPSHOT"
    idea
}

val modVersion = project.property("mod_version").toString()
val modGroupId = project.property("mod_group_id").toString()
val modId = project.property("mod_id").toString()

val minecraftVersion = project.property("minecraft_version").toString()
val minecraftVersionRange = project.property("minecraft_version_range").toString()
val neoVersion = project.property("neo_version").toString()
val modName = project.property("mod_name").toString()
val modLicense = project.property("mod_license").toString()

version = modVersion
group = modGroupId

base {
    archivesName.set(modId)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    withSourcesJar()
}

repositories {
    maven {
        url = uri("https://maven.neoforged.net/releases/")
    }
    maven {
        url = uri("https://repo.codemc.io/repository/relativitymc/")
    }
}

sourceSets {
    named("main") {
        resources {
            srcDir("src/generated/resources")
            exclude("**/*.bbmodel")
            exclude("src/generated/**/.cache")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    forgeUserdev("net.neoforged:neoforge:$neoVersion:userdev")
}

val generateModMetadata = tasks.register<ProcessResources>("generateModMetadata") {
    val replaceProperties = mapOf(
        "minecraft_version" to minecraftVersion,
        "minecraft_version_range" to minecraftVersionRange,
        "neo_version" to neoVersion,
        "mod_id" to modId,
        "mod_name" to modName,
        "mod_license" to modLicense,
        "mod_version" to modVersion
    )

    inputs.properties(replaceProperties)

    from("src/main/templates")
    into(layout.buildDirectory.dir("generated/sources/modMetadata"))

    expand(replaceProperties)
}

sourceSets {
    named("main") {
        resources.srcDir(generateModMetadata)
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            url = uri("${project.projectDir}/repo")
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.named<Wrapper>("wrapper") {
    distributionType = Wrapper.DistributionType.BIN
}

idea {
    module {
        isDownloadSources = true
        isDownloadJavadoc = true
    }
}