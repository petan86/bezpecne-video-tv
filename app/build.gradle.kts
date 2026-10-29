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
        versionCode = 24
        versionName = "0.1.23"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
