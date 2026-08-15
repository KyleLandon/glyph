description = "Glyph Discord companion bot (JDA) — identity link, role sync, alpha access."

plugins {
    application
}

dependencies {
    implementation(project(":glyph-api"))

    implementation(libs.jda) {
        exclude(group = "club.minnced", module = "opus-java")
        exclude(group = "com.google.crypto.tink", module = "tink")
    }
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.lettuce)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj)
}

application {
    mainClass.set("com.glyph.discord.GlyphDiscordMain")
}

tasks.jar {
    dependsOn(configurations.runtimeClasspath)
    manifest {
        attributes["Main-Class"] = "com.glyph.discord.GlyphDiscordMain"
    }
    // Fat jar for desktop/ops runs.
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}
