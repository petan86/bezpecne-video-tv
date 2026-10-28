plugins {
    id("com.android.application")
}

android {
    namespace = "cz.petr.bezpecnevideo"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.petr.bezpecnevideo"
        minSdk = 26
        targetSdk = 36
        versionCode = 21
        versionName = "0.1.20"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
