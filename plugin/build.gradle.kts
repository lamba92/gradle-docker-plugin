@file:Suppress("UnstableApiUsage")

plugins {
    `kotlin-dsl`
    alias(libs.plugins.gradle.publish.plugin)
    alias(libs.plugins.ktlint)
}

group = "io.github.lamba92"

val githubRef =
    System
        .getenv("GITHUB_EVENT_NAME")
        ?.takeIf { it == "release" }
        ?.let { System.getenv("GITHUB_REF") }
        ?.removePrefix("refs/tags/")
        ?.removePrefix("v")

version =
    when {
        githubRef != null -> githubRef
        else -> "1.0-SNAPSHOT"
    }

kotlin {
    jvmToolchain(8)
    target {
        withSourcesJar(true)
    }
    explicitApi()
}

gradlePlugin {
    website = "https://github.com/lamba92/gradle-docker-plugin"
    vcsUrl = "https://github.com/lamba92/gradle-docker-plugin"
    plugins {
        create("dockerPlugin") {
            id = "io.github.lamba92.docker"
            displayName = "Gradle Docker Plugin"
            implementationClass = "io.github.lamba92.gradle.docker.DockerPlugin"
            tags = listOf("docker", "ci/cd", "container", "jvm")
            description = "Integrate seamlessly Docker in your build."
        }
    }
}

publishing {
    repositories {
        maven(layout.buildDirectory.dir("testRepository")) {
            name = "test"
        }
    }
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit5"))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        environment(
            "TEST_PROJECT_PATH",
            rootProject
                .layout
                .projectDirectory
                .dir("test-project")
                .asFile.absolutePath,
        )
        testLogging {
            showExceptions = true
            showCauses = true
            showStandardStreams = true
            showStackTraces = true
        }
    }
}
