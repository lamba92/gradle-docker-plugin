# Lamba's Gradle Docker Plugin

This plugin is a Gradle plugin that provides tasks to build, push and run Docker images. 
It integrates with the JVM ecosystem to have no configuration for the most common use cases.

# Table of Contents

1. [Overview](#lambas-gradle-docker-plugin)
2. [Features](#features)
3. [Usage](#usage)
4. [Tasks](#tasks)
    - [Build Tasks](#build)
    - [Push Tasks](#push)
    - [Run Tasks](#run)
5. [CI/CD Configuration](#cicd-configuration-for-docker-plugin)
    - [Single-Platform Build and Push](#single-platform-build-and-push)
    - [Multi-Platform Build and Push](#multi-platform-build-and-push)

## Features

- Supports building, pushing and running Docker images.
- Integrates seamlessly with the JVM ecosystem for common use cases.
- Provides preconfigured support for various Docker registries:
    - GitHub Container Registry
    - Docker Hub
    - Amazon ECR
    - Google Artifact Registry
    - Custom registries with flexible configuration.
- Customizable image configurations:
    - Define image names, tags, and build arguments.
    - Automatic Dockerfile generation for JVM applications with Gradle's `application` plugin.
    - Support for multi-platform builds using Docker Buildx.
- Includes predefined Gradle tasks for:
    - Building images (`dockerBuild`, `dockerBuildxBuild`).
    - Pushing images (`dockerPush`).
    - Running images (`dockerRun`).
- Extensible to define and configure additional Docker images and registries.
- Compatible with CI/CD workflows, such as GitHub Actions.

# Usage

See the [Releases](https://github.com/lamba92/gradle-docker-plugin/releases) page for the latest version.

```kotlin
// build.gradle.kts
plugins {
    id("io.github.lamba92.docker") version "{latest-version}"
    
    // if building a JVM application
    kotlin("jvm") version "{kotlin-version}" // or any other JVM plugin
    application
}

application {
    // This is the entry point of the JVM application
    mainClass= "com.example.MainKt"
}

docker {
    registries {
        githubContainerRegistry(githubUsername = "lamba92")
        dockerHub(dockerHubUsername = "lamba92")
        amazonEcr(accountId = "123456789012", region = "us-east-1")
        googleArtifactRegistry(
            projectId = "my-project-id",
            region = "us-central1",
            registryName = "my-registry"
        )
        
        // you can define as many registries as you want and configure them as you like
        register("custom-registry") {
            // the `imageTagPrefix` is used to prefix the image tag when pushing the image as required by Docker registries.
            imageTagPrefix = "my-registry.com"
        }
    }
    
    images {
        // the `main` image is the one that will be built by default
        main { 
            imageName = project.name // default
            imageTag = provider { project.version.toString() } // default
            isLatestTag = true // default, if true, the image will have an additional tag`latest`
            buildArgs = emptyMap() // default
            platforms = listOf("linux/amd64", "linux/arm64") // default, used for task `dockerBuildxBuild` and `dockerBuildxPush`
        }
        
        // you can define as many images as you want and configure them as you like
        register("custom-image") {
            imageName = "my-custom-image"
            imageTag = "1.0.0"
            isLatestTag = false
            buildArgs = mapOf("key" to "value")
            platforms = listOf("linux/amd64", "linux/arm64/v8", "linux/arm/v7")
            
            // Configure the directory where the command `docker build` is executed
            files { // this: CopySourceSpec
                from("path/to/files")
                from("path/to/Dockerfile")
            }
        }
    }

    // Configure an image to run the JVM application provided by the `application` plugin; the Dockerfile will be generated automagically.
    // On the `main` image, this is the default configuration if the `application` plugin is applied.
    configureJvmApplication(images.main) { // this: CreateJvmDockerfile
        baseImageName = "eclipse-temurin" // default
        baseImageTag = "21-alpine" // default

        additionalConfig = 
            """
            RUN echo "Hello, World!"
            """.trimIndent()
    }
}
```
## Tasks

### build
- `dockerBuild` - Builds all Docker images.
- `dockerBuild{ImageName}` - Builds a specific Docker image.

- `dockerBuildxBuild` - Builds all Docker images using [Buildx](https://github.com/docker/buildx) for the specified platforms in the image configuration.
- `dockerBuildxBuild{ImageName}` - Builds `main` Docker image using [Buildx](https://github.com/docker/buildx).

### push

**NOTE** Login to the registries is required OUTSIDE the plugin and Gradle because of how the Docker CLI works.

- `dockerPush`: Builds and pushes all Docker images to the registered registries. 
- `dockerPush{ImageName}To{RegistryName}`: Builds and pushes a specific Docker image to a specific registry.
- `dockerPushAllImagesTo{RegistryName}`: Builds and pushes all Docker images to a specific registry.

### run
CLI command used is `docker run --rm {imageName}:{imageTag}`:

- `dockerRun` - Runs the main Docker image.
- `dockerRun{ImageName}` - Runs a specific Docker image (excluding the `main` image).

# CI/CD Configuration for Docker Plugin

The following sections detail how to set up CI/CD pipelines for building and pushing Docker images using Gradle's Docker Plugin. Both single-platform and multi-platform workflows are covered, with examples tailored for GitHub Actions.

---

## Single-Platform Build and Push

This workflow builds and pushes Docker images for a single platform. It assumes the use of the `dockerPush` Gradle task and configuration of the `main` Docker image in the Gradle file.

### Prerequisites

1. **Repository Permissions**:
    - `contents: read`
    - `packages: write`

2. **Gradle Configuration**:
   Ensure your Gradle `docker` block is configured correctly to push Docker images.

### Example Workflow

```yaml
name: Build and Push Docker Image

on:
  push:
    branches:
      - main

permissions:
  contents: read
  packages: write

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    name: Build and Push Docker Image
    steps:
      # Checkout the repository
      - uses: actions/checkout@v4

      # Set up Java (required for Gradle)
      - uses: actions/setup-java@v4
        with:
          distribution: adopt
          java-version: 21

      # Set up Gradle
      - uses: gradle/actions/setup-gradle@v4

      # Grant execution rights to Gradle wrapper
      - run: chmod +x gradlew

      # Log in to Docker Registry (GitHub Container Registry in this case)
      - name: Log in to GitHub Container Registry
        run: echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin

      # Build and push the Docker image
      - name: Build and Push Docker Image
        run: ./gradlew dockerPush
```

### Key Notes

- The `dockerPush` task builds and pushes all defined images in the `docker` block to the configured registries.
- Use GitHub's `GITHUB_TOKEN` for authentication with GitHub Container Registry.

---

## Multi-Platform Build and Push

This workflow utilizes Docker Buildx to create multi-platform images. Specify the target platforms in your Gradle `docker` configuration.

### Prerequisites

1. **Enable Docker Buildx**:
   Use the `docker/setup-buildx-action` GitHub Action to enable Docker Buildx on the GitHub runner.

2. **Authentication**:
   Ensure your `GITHUB_TOKEN` has permissions to read (`contents: read`) and write (`packages: write`) to the Docker registry.

3. **Gradle Configuration**:
   In your Gradle `docker` block, set the `platforms` property for multi-platform builds:
   ```kotlin
   docker {
       images {
           main {
               platforms = listOf("linux/amd64", "linux/arm64")
           }
       }
   }
   ```

### Example Workflow

```yaml
name: Build and Push Multi-Platform Docker Image

on:
  push:
    branches:
      - main

permissions:
  contents: read
  packages: write

jobs:
  build-and-push-multiplatform:
    runs-on: ubuntu-latest
    name: Build and Push Multi-Platform Docker Image
    steps:
      # Checkout the repository
      - uses: actions/checkout@v4

      # Set up Java (required for Gradle)
      - uses: actions/setup-java@v4
        with:
          distribution: adopt
          java-version: 21

      # Set up Gradle
      - uses: gradle/actions/setup-gradle@v4

      # Grant execution rights to Gradle wrapper
      - run: chmod +x gradlew

      # Set up Docker Buildx
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      # Log in to Docker Registry (GitHub Container Registry in this case)
      - name: Log in to GitHub Container Registry
        run: echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin

      # Build and push the multi-platform Docker image
      - name: Build and Push Multi-Platform Docker Image
        run: ./gradlew dockerBuildxPush
```

### Key Notes

- Multi-platform support is extremely useful for environments requiring ARM architecture support, such as Raspberry Pi or AWS Graviton.
- Ensure proper registry permissions when pushing images, especially for private Docker registries.