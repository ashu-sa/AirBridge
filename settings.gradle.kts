pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = java.net.URI("https://jitpack.io") }
    }
}

rootProject.name = "FitbitVibeBridge"
include(":app")

// Separate opt-in test sender; excluded from normal app builds.
if (providers.gradleProperty("notificationFixture").orNull == "true") include(":notification-fixture")
