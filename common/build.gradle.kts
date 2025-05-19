plugins {
    `java-library`
    kotlin("jvm")
}

group = "dev.hollowcube"
version = System.getenv("TAG_VERSION") ?: "dev"
description = "Fast and small world format for Minestom"

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.0.2")
    api("net.kyori:adventure-api:4.21.0")
    api("net.kyori:adventure-nbt:4.21.0")
    api(libs.zstd)
    // Fastutil is only included because minestom already uses it, otherwise it is a crazy dependency
    // for how it is used in this project.
    api(libs.fastutil)

    testImplementation("ch.qos.logback:logback-core:1.4.7")
    testImplementation("ch.qos.logback:logback-classic:1.4.7")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation(kotlin("stdlib-jdk8"))
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.test {
    maxHeapSize = "2g"
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}