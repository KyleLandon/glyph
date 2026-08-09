description = "GlyphCore Folia plugin: platform foundation (config, scheduling, PostgreSQL, Redis, health)."

dependencies {
    api(project(":glyph-api"))

    compileOnly(libs.folia.api)

    // Provided at runtime through the `libraries:` section of plugin.yml
    // (Paper/Folia downloads them from Maven Central at startup).
    compileOnly(libs.hikaricp)
    compileOnly(libs.flyway.core)
    compileOnly(libs.postgresql)
    compileOnly(libs.lettuce)

    // Classic Vault economy interfaces; VaultUnlocked ships them at runtime.
    compileOnly(libs.vault.api) { isTransitive = false }
    testImplementation(libs.vault.api) { isTransitive = false }

    testImplementation(libs.folia.api)
    testImplementation(libs.hikaricp)
    testImplementation(libs.flyway.core)
    testRuntimeOnly(libs.flyway.postgresql)
    testImplementation(libs.postgresql)
    testImplementation(libs.lettuce)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj)
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.slf4j.simple)
}

// The plugin jar must ship the glyph-api classes (Paper does not know about
// our Gradle modules). Other plugins compile against glyph-api; GlyphCore
// provides it at runtime.
tasks.jar {
    val apiJar = project(":glyph-api").tasks.named<Jar>("jar")
    dependsOn(apiJar)
    from(apiJar.flatMap { it.archiveFile }.map { zipTree(it) }) {
        exclude("META-INF/**")
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "hikaricpVersion" to libs.versions.hikaricp.get(),
        "flywayVersion" to libs.versions.flyway.get(),
        "postgresqlVersion" to libs.versions.postgresql.get(),
        "lettuceVersion" to libs.versions.lettuce.get(),
    )
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}
