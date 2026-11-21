import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.compose.compiler)
}

val goldenSources = tasks.register<EmbedGoldenFrames>("embedGoldenFrames") {
    captureFrames.set(providers.gradleProperty("lottoriCaptureFrames").map { it.toBoolean() }.orElse(false))
    fixtures.set(rootProject.layout.projectDirectory.dir("fixtures"))
    output.set(layout.buildDirectory.dir("generated/test-fixtures"))
}

kotlin {
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
    jvm()
    android {
        namespace = "com.ycgc.lottori"
        compileSdk = 37
        minSdk = 23
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            implementation(libs.json)
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.coroutines)
        }
        commonTest {
            kotlin.srcDir(goldenSources.flatMap { it.output })
            dependencies { implementation(kotlin("test")) }
        }
        getByName("androidDeviceTest").dependencies {
            implementation("androidx.test:runner:1.7.0")
            implementation(kotlin("test-junit"))
        }
        jvmTest {
            dependencies {
                val os = System.getProperty("os.name").lowercase()
                val platform = when {
                    os.contains("mac") -> "macos"
                    os.startsWith("windows") -> "windows"
                    else -> "linux"
                }
                val arch = if (System.getProperty("os.arch") in setOf("arm64", "aarch64")) "arm64" else "x64"
                implementation("org.jetbrains.compose.desktop:desktop-jvm-$platform-$arch:${libs.versions.compose.get()}")
            }
        }
    }
}
