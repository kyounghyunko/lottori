plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":lottori"))
}
application {
    mainClass.set("com.ycgc.lottori.check.MainKt")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
