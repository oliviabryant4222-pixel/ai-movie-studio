plugins { id("com.android.application") }

android {
    namespace = "studio.realframe"
    compileSdk = 35
    defaultConfig {
        applicationId = "studio.realframe.v17"
        minSdk = 29
        targetSdk = 35
        versionCode = 17
        versionName = "1.7.0-hybrid-batch-beta"
        ndk { abiFilters.add("arm64-v8a") }
    }
    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
