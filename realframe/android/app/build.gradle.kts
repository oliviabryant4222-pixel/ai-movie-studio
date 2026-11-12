plugins { id("com.android.application") }

android {
    namespace = "studio.realframe"
    compileSdk = 35
    defaultConfig {
        applicationId = "studio.realframe.v14"
        minSdk = 29
        targetSdk = 35
        versionCode = 14
        versionName = "1.4.0-beta"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Offline still photo -> MP4 transformation.
dependencies {
    implementation("androidx.media3:media3-transformer:1.9.4")
    implementation("androidx.media3:media3-common:1.9.4")
}
