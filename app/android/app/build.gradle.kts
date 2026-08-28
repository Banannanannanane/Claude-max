import java.util.Properties

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// Clés de signature de publication. Le fichier android/key.properties n'est pas
// versionné : en local il est créé par le développeur, en CI il est reconstitué
// depuis les secrets du dépôt (voir .github/workflows/android.yml).
// Sans lui, le build release retombe sur la clé de debug — installable sur un
// appareil, mais refusé par le Play Store.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("key.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasUploadKeystore = keystoreProperties.containsKey("storeFile")

android {
    namespace = "fr.capart.ca_part"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "fr.capart.ca_part"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        resourceConfigurations += listOf("fr", "en")
    }

    // Deux applications à partir du même code. Les identifiants diffèrent,
    // donc elles s'installent côte à côte : on garde celle qui tourne pendant
    // qu'on essaie l'autre.
    //
    // Gradle interdit un flavor nommé « test » (le nom est réservé aux
    // source sets de test), d'où « dev » côté build — le nom affiché sous
    // l'icône, lui, est bien « Ça Part test ».
    flavorDimensions += "app"
    productFlavors {
        create("prod") {
            dimension = "app"
            resValue("string", "app_name", "Ça Part")
        }
        create("dev") {
            dimension = "app"
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            resValue("string", "app_name", "Ça Part test")
        }
    }

    signingConfigs {
        if (hasUploadKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasUploadKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter {
    source = "../.."
}
