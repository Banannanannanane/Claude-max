plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":engine"))
}

application {
    // ./gradlew :preview:run --args="docs/preview"
    mainClass.set("dev.taskbarhero.preview.PreviewMainKt")
}
