@file:Suppress("UnstableApiUsage")

plugins {
    kotlin("jvm") version "2.2.0"
    application
    id("io.github.lamba92.docker")
    alias(libs.plugins.ktlint)
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.github.lamba92"
version = "1.0"

kotlin {
    jvmToolchain(8)
}

application {
    mainClass = "io.github.lamba92.gradle.docker.tests.MainKt"
}

docker {
    registries {
        System
            .getenv("REPOSITORY_OWNER")
            ?.let { githubContainerRegistry(it) }
    }
    configureJvmApplication(images.main) {
        baseImageName = "eclipse-temurin"
        baseImageTag = "21"
        additionalConfig =
            """
            RUN echo "Hello, World!"
            """.trimIndent()
    }
    images {
        all {
            System.getenv("IMAGE_VERSION")?.let { imageVersion = it }
        }

        // Example on how to register a new image to use the
        // shadow plugin. The tasks dockerXXXShadow is available
        val shadow by registering
        configureJvmApplication(shadow, tasks.installShadowDist)
    }
}
