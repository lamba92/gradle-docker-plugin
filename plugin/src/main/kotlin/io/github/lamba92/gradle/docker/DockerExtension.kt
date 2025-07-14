@file:Suppress("ktlint:standard:no-unused-imports")

package io.github.lamba92.gradle.docker

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.distribution.plugins.DistributionPlugin
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * Represents a Docker extension entity, managing Docker images and registries configuration
 * within a Gradle project context.
 *
 * @param extensionName The name of the extension.
 * @property images A container for managing Docker images.
 * @property registries A container for managing Docker registries.
 */
public abstract class DockerExtension(
    private val project: Project,
    public val extensionName: String,
    public val images: DockerImageContainer,
    public val registries: DockerRegistryContainer,
) : ExtensionAware,
    Named {
    override fun getName(): String = extensionName

    /**
     * Configures the Docker registries container using the specified configuration action.
     *
     * @return The configured `DockerRegistriesContainer` instance.
     */
    public fun registries(action: DockerRegistryContainer.() -> Unit): DockerRegistryContainer = registries.apply(action)

    /**
     * Configures the Docker images container using the specified configuration action.
     *
     * @return The configured `DockerImagesContainer` instance.
     */
    public fun images(action: DockerImageContainer.() -> Unit): DockerImageContainer = images.apply(action)

    /**
     * Configures a Docker image for a JVM application by generating a Dockerfile and bundling
     * necessary application distribution files. This method assumes that the Gradle 'application' plugin
     * is applied to the project. If the plugin is not applied, an error message is logged, and the
     * configuration is skipped.
     *
     * @param image The Docker image configuration represented by a `NamedDomainObjectProvider<DockerImage>`.
     * This parameter holds the settings for the Docker image to be built, such as its name, tags, and
     * platform support.
     * @param distributionTask The Gradle `Sync` task responsible for creating the application distribution
     * (by default, the task named by `DistributionPlugin.TASK_INSTALL_NAME`). This task ensures that the
     * necessary application files are available for inclusion in the Docker image.
     * @param action An optional lambda that configures the `CreateJvmDockerfile` task. This can be used
     * to adjust the default Dockerfile generation logic, such as setting a custom base image or additional
     * configuration for the file.
     */
    public fun configureJvmApplication(
        image: NamedDomainObjectProvider<DockerImage>,
        distributionTask: TaskProvider<Sync> = project.tasks.named<Sync>(DistributionPlugin.TASK_INSTALL_NAME),
        action: CreateJvmDockerfile.() -> Unit = { },
    ) {
        val hasApplicationPlugin = project.plugins.hasPlugin("org.gradle.application")
        if (!hasApplicationPlugin) {
            project.logger.error(getJvmAppErrorMessage(image.name, project.path))
            return
        }

        val createDockerfileTaskName =
            buildString {
                append("create")
                append(image.name.toCamelCase())
                append("JvmAppDockerfile")
            }

        val alreadyRegistered = createDockerfileTaskName in project.tasks.names
        if (alreadyRegistered) {
            project.logger.info("Task $createDockerfileTaskName already registered, skipping registration.")
            project.tasks.named<CreateJvmDockerfile>(createDockerfileTaskName, action)
            return
        }

        val createDockerfileTask =
            project.tasks.register<CreateJvmDockerfile>(createDockerfileTaskName) {
                group = "docker"
                appName = project.name
                val nameProvider =
                    project.provider { "dockerfiles/${project.name}-${baseImageName.get()}-${baseImageTag.get()}.dockerfile" }
                destinationFile =
                    project
                        .layout
                        .buildDirectory
                        .file(nameProvider)
                action()
            }

        image {
            files {
                from(distributionTask)
                from(createDockerfileTask) {
                    rename { "Dockerfile" }
                }
            }
        }
    }
}
