pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository { maven { url = file("../../build/repository").toURI() } }
            filter { includeGroup("com.ycgc.lottori") }
        }
        google()
        mavenCentral()
    }
    versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}
rootProject.name = "lottori-verification-consumer"
