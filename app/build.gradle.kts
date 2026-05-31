plugins {
    id("com.android.application")
}

android {
    namespace = "com.kirix.schedule"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kirix.schedule"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "2.3"
    }

    signingConfigs {
        create("releaseDebug") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("releaseDebug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
