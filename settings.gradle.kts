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
    }
}

rootProject.name = "TrailGuardian"

include(":app")
include(":core:model")
include(":core:geo")
include(":core:gpx")
include(":core:data")
include(":core:ui")
include(":service:recording")
include(":feature:map")
include(":feature:workspace")
include(":feature:history")
include(":feature:settings")
include(":feature:offline")
