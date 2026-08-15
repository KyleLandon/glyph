description = "GlyphProxy Velocity plugin: proxy-side platform foundation."

dependencies {
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)

    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj)
}

tasks.jar {
    // Velocity does not download plugin libraries — shade JDBC deps.
    from({
        configurations.runtimeClasspath.get()
            .filter { file ->
                val name = file.name
                name.startsWith("HikariCP")
                        || name.startsWith("postgresql")
                        || name.startsWith("slf4j-api")
            }
            .map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
