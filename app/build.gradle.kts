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
        versionCode = 14
        versionName = "0.1.13"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
