import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.kmp) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.maven.publish) apply false
}

val releaseVersion = providers.gradleProperty("lottoriReleaseVersion").orNull
require(releaseVersion == null || (releaseVersion.isNotBlank() && !releaseVersion.endsWith("-SNAPSHOT"))) {
    "lottoriReleaseVersion must be a non-snapshot release version"
}

subprojects {
    group = "com.ycgc.lottori"
    version = releaseVersion ?: "0.1.0-SNAPSHOT"

    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        extensions.configure<PublishingExtension> {
            repositories.maven {
                name = "verification"
                url = rootProject.layout.buildDirectory.dir("repository").get().asFile.toURI()
            }
        }
        extensions.configure<MavenPublishBaseExtension> {
            if (releaseVersion != null) {
                publishToMavenCentral(automaticRelease = false)
                signAllPublications()
            }
            pom {
                name.set(project.name)
                description.set("2D Lottie player for Compose Multiplatform")
                val repository = "https://github.com/kyounghyunko/lottori"
                url.set(repository)
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("kyounghyunko")
                        name.set("kyounghyunko")
                        url.set("https://github.com/kyounghyunko")
                    }
                }
                scm {
                    connection.set("scm:git:$repository.git")
                    developerConnection.set("scm:git:ssh://git@github.com/kyounghyunko/lottori.git")
                    url.set(repository)
                }
            }
        }
    }
}
