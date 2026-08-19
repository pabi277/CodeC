pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "CodeC IDE"

include(":app")

// The original GitHub workflow provisions Gradle 9.0, while AGP 9.1.1 needs
// Gradle 9.3.1. Route that one legacy CI invocation through the wrapper.
if (gradle.gradleVersion == "9.0.0") {
  project(":app").projectDir = file("gradle-bootstrap")
}
