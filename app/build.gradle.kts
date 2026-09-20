plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.autochat.floating"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.autochat.floating"
        minSdk = 24
        targetSdk = 34
        versionCode = 19
        versionName = "3.2.0"
        setProperty("archivesBaseName", "TikTokAutoChat-v3.2.0")
    }

    signingConfigs {
        create("release") {
            val ksFile = file("../release.keystore")
            if (ksFile.exists()) {
                storeFile = ksFile
                storePassword = "autochatrelease"
                keyAlias = "autochat"
                keyPassword = "autochatrelease"
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val ksFile = file("../release.keystore")
            if (ksFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        getByName("debug") {
            applicationIdSuffix = ""
            versionNameSuffix = ""
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}
