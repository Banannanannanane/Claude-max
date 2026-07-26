plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.dotmatrix.taskbarhero"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.dotmatrix.taskbarhero"
        // Nothing Phone (1) shipped on Android 12; targetCellWidth/Height and
        // widget descriptions need API 31 anyway.
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.core.ktx)
}
