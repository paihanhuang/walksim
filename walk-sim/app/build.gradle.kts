plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.pikmin.walksim"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pikmin.walksim"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
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

    // Kotlin 2.1.0 ships its own Compose compiler via the org.jetbrains.kotlin.plugin.compose plugin
    // (applied above), so no legacy composeOptions{ kotlinCompilerExtensionVersion } is needed here.
    buildFeatures {
        compose = true
    }

    testOptions {
        // The extracted WalkSessionController keeps its diagnostic android.util.Log calls; without this the
        // stub android.jar throws "not mocked" on them in the pure JVM controller test. Test-only; no prod effect.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core-model"))
    implementation(project(":core-sim"))
    implementation(project(":core-osm"))
    implementation(libs.osmdroid.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.location)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test) // virtual-time runTest for the suspend controller matrix
}

// JVM unit tests for the pure logic (mapping / state-machine / pace) use JUnit5, like the :core-* modules.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
