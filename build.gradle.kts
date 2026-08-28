plugins {
    `java-library`
    `maven-publish`
}

group = "net.hollowcube"
version = "1.16.0-fabric"

repositories {
    mavenCentral()
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveBaseName = "polar"
}
