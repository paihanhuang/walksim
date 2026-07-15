plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Pure data types — no production dependencies, no Android.
dependencies {
    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
