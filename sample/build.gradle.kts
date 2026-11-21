plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.compose.compiler)
}

val sampleMode = providers.gradleProperty("lottoriSampleMode").orElse("demo").get()
require(sampleMode in setOf("demo", "baseline", "player", "json", "dotlottie", "perf"))

tasks.register<Sync>("prepareSampleAssets") {
    into(layout.buildDirectory.dir("sample-assets/$sampleMode"))
    when (sampleMode) {
        "demo" -> {
            from(rootProject.file("docs/assets/paper-flight.json"))
            from(rootProject.file("fixtures")) {
                include("moving-circle.json", "static-circle.json", "trim-path.json")
            }
        }
        "json", "perf" -> from(rootProject.file("fixtures/moving-circle.json")) { rename { "input.json" } }
        "dotlottie" -> from(file(providers.gradleProperty("lottoriSampleAsset").get())) { rename { "input.lottie" } }
    }
}

kotlin {
    android {
        namespace = "com.ycgc.lottori.sample.shared"
        compileSdk = 37
        minSdk = if (sampleMode == "perf") 24 else 23
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework { baseName = "LottoriSampleKit" }
    }
    if (sampleMode == "perf") {
        sourceSets.getByName("androidMain").kotlin.srcDir("src/perfAndroid/kotlin")
        sourceSets.matching { it.name == "iosMain" }.all { kotlin.srcDir("src/perfIos/kotlin") }
    }
    sourceSets.commonMain {
        val source = when (sampleMode) {
            "demo", "baseline", "perf" -> sampleMode
            else -> "player"
        }
        kotlin.srcDir("src/$source/kotlin")
        dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.runtime)
            if (sampleMode != "baseline") implementation(project(":lottori"))
            if (sampleMode == "perf") implementation("io.github.alexzhirkevich:compottie-lite:2.3.3")
        }
    }
}
