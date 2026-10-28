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
        versionCode = 9
        versionName = "0.1.8"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
