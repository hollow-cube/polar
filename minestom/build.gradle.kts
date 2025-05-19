plugins {
    `java-library`
}

repositories {
    mavenLocal()
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation(project(":common"))
    val minestom = libs.minestom
    compileOnly(minestom)

    testImplementation("ch.qos.logback:logback-core:1.4.7")
    testImplementation("ch.qos.logback:logback-classic:1.4.7")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(minestom)
}