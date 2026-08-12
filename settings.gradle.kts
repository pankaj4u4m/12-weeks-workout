pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_PROJECT (not FAIL_ON_PROJECT_REPOS): the Kotlin/Wasm plugin auto-registers its own
    // project-level ivy repository (nodejs.org/dist) to download the Node.js toolchain used by
    // wasmJs browser tasks; FAIL_ON_PROJECT_REPOS rejects it and PREFER_SETTINGS silently drops
    // it (Node.js download then 404s against google()/mavenCentral()).
    // lc-debt: revisit with an explicit centralized ivy repo for org.nodejs:node if stricter
    // repo governance (FAIL_ON_PROJECT_REPOS) is required later.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TwelveWeek"
include(":app")
