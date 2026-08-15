description = "Public API consumed by trusted plugins. Implementation lives in glyph-core."

java {
    withSourcesJar()
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj)
}
