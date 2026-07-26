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

rootProject.name = "taskbar-hero-widget"

// :engine is plain Kotlin/JVM and holds the whole simulation, so the game logic
// can be tested without an emulator. :app only renders it.
include(":engine")
include(":app")
// Renders the very same layouts to PNG on a JVM, so the design can be reviewed
// without an emulator. Not shipped in the APK.
include(":preview")
