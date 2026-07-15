plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":core-model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json) // DOM-only parse (parseToJsonElement); no compiler plugin needed
    testImplementation(project(":core-sim")) // sweepRoute + WalkPlayer/GraphRandomWalker for the route/integration tests only (geodesy is in :core-model)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    useJUnitPlatform()
}
