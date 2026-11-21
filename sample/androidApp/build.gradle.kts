plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val perfMode = providers.gradleProperty("lottoriSampleMode").orNull == "perf"

android {
    namespace = "com.ycgc.lottori.sample"
    compileSdk = 37
    defaultConfig {
        applicationId = if (perfMode) "com.ycgc.lottori.sample.perf" else "com.ycgc.lottori.sample"
        minSdk = if (perfMode) 24 else 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
    buildTypes.named("release") {
        isMinifyEnabled = true
        if (perfMode) signingConfig = signingConfigs.getByName("debug")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
    buildFeatures { compose = true }
    val mode = providers.gradleProperty("lottoriSampleMode").orElse("demo").get()
    sourceSets["main"].assets.directories.add(project(":sample").layout.buildDirectory.dir("sample-assets/$mode").get().asFile.absolutePath)
}

tasks.named("preBuild") { dependsOn(":sample:prepareSampleAssets") }

dependencies {
    implementation(project(":sample"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation(libs.compose.runtime)
}
