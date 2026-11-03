pluginManagement {
    repositories {
        google()
        // Google's mirror of Maven Central — far less prone to rate limiting than
        // repo.maven.apache.org on shared CI/build hosts. Central stays as a fallback.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}

rootProject.name = "Depth"
include(":app")
