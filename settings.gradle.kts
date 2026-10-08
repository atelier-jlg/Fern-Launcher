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

rootProject.name = "FernLauncher"
include(":app")      // Fern Launcher
include(":theme")    // commun : couleurs, police, thème partagé
include(":messages") // Fern Messages (SMS/MMS)
include(":contact")  // Fern Contact (contacts + téléphone)
