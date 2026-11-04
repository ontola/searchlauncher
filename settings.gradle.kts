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
        // F-Droid builds with -Pengine=webview and may not use this repository. Its recipe also
        // deletes this line, since its scanner rejects any unknown repository URL it can see.
        if (providers.gradleProperty("engine").getOrElse("gecko") != "webview") maven("https://maven.mozilla.org/maven2/") { content { includeGroup("org.mozilla.geckoview") } }
    }
}

rootProject.name = "SearchLauncher"
include(":app")

include(":performance")
