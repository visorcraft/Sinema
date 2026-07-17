plugins {
    id("com.android.application")
}

android {
    namespace = "com.sinema"
    compileSdk = 36

    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.sinema"
        minSdk = 24
        targetSdk = 36
        versionCode = 21
        versionName = "1.16.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.leanback:leanback:1.2.0")
    // preferences handled manually
    
    // Media3 ExoPlayer
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation("androidx.media3:media3-ui-leanback:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    
    // Image loading
    implementation("com.github.bumptech.glide:glide:5.0.7")
    
    // HTTP/JSON
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.google.code.gson:gson:2.14.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.tvprovider:tvprovider:1.1.0")

    testImplementation("junit:junit:4.13.2")
}
