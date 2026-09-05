// Exception unique à la règle « zéro version inline » : Gradle ne permet pas de référencer
// un catalogue de versions depuis le bloc plugins du fichier settings lui-même
// (limitation plateforme — le catalogue n'est pas visible ici). Cette version inline est
// donc la seule du dépôt, documentée et auditée (plan 01-01, tâche 3).
// NB : en DSL Kotlin, pluginManagement doit précéder plugins (contrainte d'ordre Gradle).
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" // exception unique — version aussi posée dans libs.versions.toml
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        // jitpack.io volontairement ABSENT en P1 — ajouté en Phase 4 avec NewPipeExtractor
    }
}
rootProject.name = "ShortifyLocal AI"
include(":app")
