plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.mammouthclient.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mammouthclient.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // Clé de signature partagée, versionnée dans le dépôt : elle garantit que toutes les
    // APK produites (localement ou par la CI) portent la même signature, condition pour
    // qu'une nouvelle version s'installe par-dessus la précédente. Ce n'est pas une clé
    // de publication : pour le Play Store, utilisez les variables RELEASE_KEYSTORE_*.
    val sharedKeystore = rootProject.file("keystore/mammouth.jks")
    val sharedSigning = signingConfigs.getByName("debug") {
        if (sharedKeystore.exists()) {
            storeFile = sharedKeystore
            storePassword = "mammouth"
            keyAlias = "mammouth"
            keyPassword = "mammouth"
        }
    }

    // Signature de release dédiée si les variables d'environnement sont fournies.
    val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
    val releaseSigning = signingConfigs.create("release") {
        if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
            storeFile = file(keystorePath)
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = sharedSigning
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Clé de publication si fournie, sinon la clé partagée pour rester installable.
            signingConfig = if (releaseSigning.storeFile != null) releaseSigning else sharedSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
}
