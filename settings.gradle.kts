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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "taskbar-hero"

// :engine is plain Kotlin/JVM — the rules of the game, and everything that can be
// tested without a device. :app is Android, and only ever renders what it is told.
include(":engine")
include(":app")
// Renders the same layouts to PNG on a desktop JVM — design review without an
// emulator. Not part of the APK.
include(":preview")
