@file:Suppress("UnstableApiUsage")
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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
rootProject.name = "TessolSampleApp"
// Function to recursively include all subprojects
fun includeSubprojects(dir: File) {
    dir.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            val buildFileKts = File(file, "build.gradle.kts")
            val buildFileGroovy = File(file, "build.gradle")
            if (buildFileKts.exists() || buildFileGroovy.exists()) {
                // Convert the path to a Gradle project path (e.g., ":submodule", ":sub:submodule")
                val projectPath = file.relativeTo(rootDir).path.replace(File.separator, ":")
                include(projectPath)
            }
            // Recursively search for nested submodules
            includeSubprojects(file)
        }
    }
}

// Start including from the root directory
includeSubprojects(rootDir)