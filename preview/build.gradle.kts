plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(17) }

dependencies { implementation(project(":engine")) }

// Renders the very same layouts the phone does, to PNG, so the design can be
// reviewed without an emulator. Never shipped in the APK.
application { mainClass.set("dev.taskbarhero.preview.PreviewMainKt") }

tasks.named<JavaExec>("run") {
    // Gradle would otherwise run this from preview/, where neither the assets nor
    // docs/ exist — so every path handed to it would have to be written relative
    // to a directory nobody thinks in. The repository root is that directory.
    workingDir = rootDir
}
