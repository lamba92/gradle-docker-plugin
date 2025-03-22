@file:Suppress("UnstableApiUsage")

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
    id("com.gradle.enterprise") version "3.19.2"
}

rootProject.name = "gradle-docker-plugin-repository"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    rulesMode = RulesMode.FAIL_ON_PROJECT_RULES
}

val isCi
    get() = System.getenv("CI") == "true"

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
        publishing {
            onlyIf { isCi }
        }
    }
}

include("example")
includeBuild("plugin")