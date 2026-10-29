pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "rag-doc-platform"

include("platform-common")
include("platform-ingestion-adapters")
include("platform-bootstrap")
include("parser-service")
