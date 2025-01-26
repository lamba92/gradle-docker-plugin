@file:Suppress("UnstableApiUsage")

plugins {
    kotlin("jvm") version "2.1.0"
    application
    id("io.github.lamba92.docker")
    alias(libs.plugins.ktlint)
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
        System.getenv("REPOSITORY_OWNER")
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
    images.main {
        System.getenv("IMAGE_VERSION")?.let { imageVersion = it }
    }
}
