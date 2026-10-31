plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.sinema.baselineprofile"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Profiles the app's release variant — that is what ships to users.
    targetProjectPath = ":app"

    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    // Generation runs against a connected emulator (see developer-reference.md
    // for the documented command); the app module commits the output into
    // :app src/release/generated so release CI builds need no emulator.
    useConnectedDevices = true
}

dependencies {
    // BaselineProfileRule (per current docs, ships with the macro library).
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
