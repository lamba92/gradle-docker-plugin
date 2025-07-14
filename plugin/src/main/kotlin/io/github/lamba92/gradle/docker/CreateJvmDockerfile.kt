package io.github.lamba92.gradle.docker

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

/**
 * A task for generating a Dockerfile configured for a JVM application. This task allows you to specify
 * the base image name and tag, along with the application's name, and writes the corresponding Dockerfile
 * to the specified destination.
 *
 * @constructor Initializes the task and its properties.
 *
 * @param objects The `ObjectFactory` used for instantiating Gradle properties.
 *
 * @property destinationFile The file property representing the destination of the generated Dockerfile.
 * @property baseImageName The name of the base Docker image to use.
 * @property baseImageTag The tag of the base Docker image to use.
 * @property appName The name of the application to include in the Dockerfile.
 *
 */
public open class CreateJvmDockerfile
    @Inject
    constructor(
        objects: ObjectFactory,
    ) : DefaultTask() {
        @get:OutputFile
        public val destinationFile: RegularFileProperty = objects.fileProperty()

        @get:Input
        public val baseImageName: Property<String> =
            objects
                .property<String>()
                .convention("eclipse-temurin")

        @get:Input
        public val baseImageTag: Property<String> =
            objects
                .property<String>()
                .convention("21-alpine")

        @get:Input
        public val appName: Property<String> = objects.property<String>()

        @get:Input
        @get:Optional
        public val additionalConfig: Property<String?> = objects.property<String?>()

        /**
         * Writes the content of a Dockerfile for a JVM application to the specified [destinationFile].
         *
         * The content is generated using the [jvmAppDockerImageString] function, which configures the Dockerfile
         * with the base image name, image tag, and application name.
         */
        @TaskAction
        public fun writeFile() {
            destinationFile
                .get()
                .asFile
                .writeText(
                    jvmAppDockerImageString(
                        imageName = baseImageName.get(),
                        imageTag = baseImageTag.get(),
                        appName = appName.get(),
                        additionalConfiguration = additionalConfig.orNull,
                    ),
                )
        }
    }
