@file:Suppress("ktlint:standard:no-unused-imports")

package io.github.lamba92.gradle.docker

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.container
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register

/**
 * A Gradle plugin for simplifying Docker image creation and management within a project.
 *
 * The plugin provides a mechanism for defining and configuring Docker images and registries
 * as well as creating associated Gradle tasks for building and pushing Docker images.
 *
 * ## Key Features:
 * - Registers a `docker` extension where users can configure images and registries.
 * - Automatically creates tasks:
 *   - `dockerBuild`: Groups tasks responsible for building Docker images.
 *   - `dockerPush`: Groups tasks responsible for pushing Docker images.
 *
 * - Adds additional Gradle tasks for Docker's Buildx:
 *   - `dockerBuildxBuild`: Groups tasks for building images using Buildx.
 *   - `dockerBuildxPush`: Groups tasks for pushing Buildx-built images.
 *
 * ## Extension:
 * The plugin registers a `docker` extension of type [DockerExtension].
 * This provides:
 * - `images`: A container for Docker image definitions.
 * - `registries`: A container for Docker registry configurations.
 *
 * Example usage can include registering additional Docker images or modifying default configurations.
 *
 * ## Default Behavior:
 * - Automatically registers a default `main` image configuration with the project extensionName as the image extensionName.
 * - If the `org.gradle.application` plugin is applied, the default image configuration will be automatically
 *   adjusted to support creating a Dockerfile for JVM applications.
 *
 * ## Tasks:
 * The following tasks are provided by default:
 * - `dockerBuild`: Aggregates all tasks related to building Docker images.
 * - `dockerPush`: Aggregates all tasks related to pushing Docker images to registries.
 * - `dockerBuildxBuild`: Aggregates tasks for building images using Buildx.
 * - `dockerBuildxPush`: Aggregates tasks for pushing Buildx-built images.
 * - `createBuildxBuilder`: Creates and uses a Docker Buildx builder.
 */
public class DockerPlugin : Plugin<Project> {
    public companion object {
        public const val EXTENSION_NAME: String = "docker"
    }

    override fun apply(target: Project): Unit =
        with(target) {
            val dockerExtension =
                extensions.create<DockerExtension>(
                    EXTENSION_NAME,
                    EXTENSION_NAME,
                    DockerImageContainer(container { DockerImage(it, project) }),
                    DockerRegistryContainer(container { DockerRegistry(it, objects) }),
                )

            val mainImage =
                dockerExtension.images.register("main") {
                    imageName = project.name
                }

            plugins.withId("org.gradle.application") {
                dockerExtension.configureJvmApplication(mainImage)
            }

            val dockerPrepareAllTask =
                tasks.register("dockerPrepare") {
                    group = "docker"
                }

            val dockerBuildAllTask =
                tasks.register("dockerBuild") {
                    dependsOn(dockerPrepareAllTask)
                    group = "build"
                }

            val dockerPushAllTask =
                tasks.register("dockerPush") {
                    dependsOn(dockerBuildAllTask)
                    group = "pushing"
                }

            val dockerBuildxBuildAllTask =
                tasks.register("dockerBuildxBuild") {
                    dependsOn(dockerPrepareAllTask)
                    group = "build"
                }

            val dockerBuildxPushAllTask =
                tasks.register("dockerBuildxPush") {
                    dependsOn(dockerPrepareAllTask)
                    group = "pushing"
                }

            configurePlugin(
                dockerExtension = dockerExtension,
                dockerBuildAllTask = dockerBuildAllTask,
                dockerPushAllTask = dockerPushAllTask,
                dockerBuildxBuildAllTask = dockerBuildxBuildAllTask,
                dockerBuildxPushAllTask = dockerBuildxPushAllTask,
                dockerPrepareAllTask = dockerPrepareAllTask,
            )
        }
}

private fun Project.configurePlugin(
    dockerExtension: DockerExtension,
    dockerBuildAllTask: TaskProvider<Task>,
    dockerPushAllTask: TaskProvider<Task>,
    dockerBuildxBuildAllTask: TaskProvider<Task>,
    dockerBuildxPushAllTask: TaskProvider<Task>,
    dockerPrepareAllTask: TaskProvider<Task>,
) {
    dockerExtension.images.all {
        val dockerPrepareDir =
            project
                .layout
                .buildDirectory
                .dir("docker/prepare/$name")

        val dockerPrepareTask =
            tasks.register<Sync>("dockerPrepare${name.toCamelCase()}") {
                with(files.get())
                into(dockerPrepareDir)
            }

        dockerPrepareAllTask {
            dependsOn(dockerPrepareTask)
        }

        val dockerBuildTask =
            tasks.register<Exec>("dockerBuild${name.toCamelCase()}") {
                group = "build"
                dependsOn(dockerPrepareTask)
                inputs.dir(dockerPrepareDir)
                executable = "docker"
                val dockerArgs =
                    buildList {
                        add("build")
                        buildArgs.get().forEach { (key, value) ->
                            addAll("--build-arg", "$key=$value")
                        }
                        addAll("-t", "${imageName.get()}:${imageVersion.get()}")
                        if (isLatestTag.get()) {
                            addAll("-t", "${imageName.get()}:latest")
                        }
                        dockerExtension.registries.forEach { registry ->
                            val prefix = registry.imageTagPrefix.get().suffixIfNot("/")
                            addAll("-t", "$prefix${imageName.get()}:${imageVersion.get()}")
                            if (isLatestTag.get()) addAll("-t", "$prefix${imageName.get()}:latest")
                        }
                        add(dockerPrepareDir.get().asFile.absolutePath)
                    }
                args(dockerArgs)
            }

        dockerBuildAllTask {
            dependsOn(dockerBuildTask)
        }

        configurePublication(
            dockerExtension = dockerExtension,
            dockerImage = this,
            dockerPushAll = dockerPushAllTask,
            dockerBuildTask = dockerBuildTask,
        )

        configureBuildx(
            dockerImage = this,
            dockerExtension = dockerExtension,
            dockerPrepareDir = dockerPrepareDir,
            dockerBuildxAllTask = dockerBuildxBuildAllTask,
            dockerBuildxPushAllTask = dockerBuildxPushAllTask,
            dockerPrepareTask = dockerPrepareTask,
        )

        val dockerRunTaskName = if (name == "main") "dockerRun" else "dockerRun${name.toCamelCase()}"
        tasks.register<Exec>(dockerRunTaskName) {
            group = "docker"
            dependsOn(dockerBuildTask)
            executable = "docker"
            args("run", "--rm", "${imageName.get()}:${imageVersion.get()}")
        }
    }
}

private fun Project.configureBuildx(
    dockerImage: DockerImage,
    dockerExtension: DockerExtension,
    dockerPrepareDir: Provider<Directory>,
    dockerBuildxAllTask: TaskProvider<Task>,
    dockerBuildxPushAllTask: TaskProvider<Task>,
    dockerPrepareTask: TaskProvider<Sync>,
) {
    fun buildxArgs(additional: MutableList<String>.() -> Unit) =
        buildList {
            addAll("buildx", "build")
            dockerImage
                .platforms
                .get()
                .takeIf { it.isNotEmpty() }
                ?.joinToString(",")
                ?.let { addAll("--platform", it) }
            dockerImage
                .buildArgs
                .get()
                .forEach { (key, value) ->
                    addAll("--build-arg", "$key=$value")
                }
            additional()
            add(dockerPrepareDir.get().asFile.absolutePath)
        }

    val dockerBuildxBuildTask =
        tasks.register<Exec>("dockerBuildxBuild${dockerImage.name.toCamelCase()}") {
            dependsOn(dockerPrepareTask)
            group = "build"
            executable = "docker"
            args(
                buildxArgs {
                    addAll("--tag", "${dockerImage.imageName.get()}:${dockerImage.imageVersion.get()}")
                    if (dockerImage.isLatestTag.get()) {
                        addAll("--tag", "${dockerImage.imageName.get()}:latest")
                    }
                    add("--load")
                },
            )
        }

    dockerBuildxAllTask {
        dependsOn(dockerBuildxBuildTask)
    }

    configureBuildxPushing(
        dockerExtension = dockerExtension,
        dockerImage = dockerImage,
        dockerBuildxPushAllTask = dockerBuildxPushAllTask,
        buildxArgs = ::buildxArgs,
        dockerPrepareTask = dockerPrepareTask,
    )
}

private fun Project.configureBuildxPushing(
    dockerExtension: DockerExtension,
    dockerImage: DockerImage,
    dockerBuildxPushAllTask: TaskProvider<Task>,
    buildxArgs: (MutableList<String>.() -> Unit) -> List<String>,
    dockerPrepareTask: TaskProvider<Sync>,
) {
    dockerExtension.registries.all {
        val dockerBuildxPushTaskName =
            "dockerBuildxPush${dockerImage.name.toCamelCase()}To${registryName.toCamelCase()}"
        val dockerBuildxPushTask =
            tasks.register<Exec>(dockerBuildxPushTaskName) {
                dependsOn(dockerPrepareTask)
                group = "build"
                executable = "docker"
                args(
                    buildxArgs {
                        val prefix = imageTagPrefix.get().suffixIfNot("/")
                        addAll("--tag", "$prefix${dockerImage.imageName.get()}:${dockerImage.imageVersion.get()}")
                        if (dockerImage.isLatestTag.get()) {
                            addAll("--tag", "$prefix${dockerImage.imageName.get()}:latest")
                        }
                        add("--push")
                    },
                )
            }
        val pushAllBuildxToThisRepositoryTaskName = "pushAllBuildxImagesTo${registryName.toCamelCase()}"
        val pushAllToThisRepositoryTask =
            tasks.getOrRegister(pushAllBuildxToThisRepositoryTaskName) {
                group = "publishing"
            }
        pushAllToThisRepositoryTask {
            dependsOn(dockerBuildxPushTask)
        }
        dockerBuildxPushAllTask {
            dependsOn(dockerBuildxPushTask)
        }
    }
}

private fun Project.configurePublication(
    dockerExtension: DockerExtension,
    dockerImage: DockerImage,
    dockerPushAll: TaskProvider<Task>,
    dockerBuildTask: TaskProvider<Exec>,
) {
    dockerExtension.registries.all {
        val prefix = imageTagPrefix.map { it.suffixIfNot("/") }
        val dockerPushTask =
            tasks.register<Exec>("dockerPush${dockerImage.name.toCamelCase()}To${registryName.toCamelCase()}") {
                dependsOn(dockerBuildTask)
                group = "publishing"
                executable = "docker"
                args(
                    "push",
                    "${prefix.get()}${dockerImage.imageName.get()}:${dockerImage.imageVersion.get()}",
                )
            }

        val dockerPushLatestTaskName =
            "dockerPush${dockerImage.name.toCamelCase()}LatestTo${registryName.toCamelCase()}"
        val dockerPushLatestTask =
            tasks.register<Exec>(dockerPushLatestTaskName) {
                dependsOn(dockerBuildTask)
                onlyIf { dockerImage.isLatestTag.get() }
                group = "publishing"
                executable = "docker"
                args("push", "${prefix.get()}${dockerImage.imageName.get()}:latest")
            }

        val pushAllToThisRepositoryTask =
            tasks.getOrRegister("dockerPushAllImagesTo${registryName.toCamelCase()}") {
                group = "publishing"
            }
        pushAllToThisRepositoryTask {
            dependsOn(dockerPushTask, dockerPushLatestTask)
        }
        dockerPushAll {
            dependsOn(dockerPushTask, dockerPushLatestTask)
        }
    }
}
