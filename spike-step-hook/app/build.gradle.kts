plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.pikmin.stephook"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pikmin.stephook"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-spike"
    }

    buildTypes {
        release { isMinifyEnabled = false }
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
    // Xposed API is provided by LSPosed at runtime — compile against it only.
    compileOnly("de.robv.android.xposed:api:82")

    // JUnit5 for the pure PaceScheduler unit tests (mirrors walk-sim/core-sim + :app).
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

// The pure PaceScheduler tests use JUnit5, run via :app:testDebugUnitTest.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
