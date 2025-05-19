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
    implementation("io.netty:netty-all:4.2.1.Final")
}