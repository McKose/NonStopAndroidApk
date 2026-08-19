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
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GymApp"
include(":app")
include(":shared")
// Ortak arayüz (Compose Multiplatform) ve onu pencere olarak açan masaüstü
// kabuğu. Masaüstü, iOS simülatörünün yokluğunda ekranları GÖRMENİN yolu:
// geliştirme Linux'ta, kullanıcı Windows'ta ve ikisinde de simülatör yok.
include(":arayuz")
include(":masaustu")
