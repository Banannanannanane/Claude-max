plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "dev.taskbarhero"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.taskbarhero"
        // 26: the oldest Android that can schedule the widget's own alarms the way
        // this game needs. Every Nothing Phone is far past it.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        debug {
            // The APK the CI publishes. Debuggable, signed with the throwaway debug
            // key every Android install trusts, so it can be side-loaded directly.
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":engine"))
}
