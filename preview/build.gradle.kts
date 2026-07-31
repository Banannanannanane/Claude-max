plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(17) }

dependencies { implementation(project(":engine")) }

// Renders the very same layouts the phone does, to PNG, so the design can be
// reviewed without an emulator. Never shipped in the APK.
application { mainClass.set("dev.taskbarhero.preview.PreviewMainKt") }
