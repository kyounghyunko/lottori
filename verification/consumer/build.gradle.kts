plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.ycgc.lottori.verification"
        compileSdk = 37
        minSdk = 23
    }
    iosArm64()
    iosSimulatorArm64()
    val libraryVersion = providers.gradleProperty("lottoriReleaseVersion").orElse("0.1.0-SNAPSHOT")
    sourceSets.commonMain.dependencies {
        implementation("com.ycgc.lottori:lottori:${libraryVersion.get()}")
    }
}
