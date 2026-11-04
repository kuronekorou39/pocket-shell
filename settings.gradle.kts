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
        // Kadb のペアリングが使う spake2-java だけは JitPack にある。他の依存をここから取らないよう絞る
        maven("https://jitpack.io") {
            content { includeGroup("com.github.Flyfish233") }
        }
    }
}
rootProject.name = "pocket-shell"
include(":app")
