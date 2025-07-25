plugins {
    `java-library`

    `maven-publish`
    signing
    alias(libs.plugins.nmcp)
}

group = "dev.hollowcube"
version = System.getenv("TAG_VERSION") ?: "dev"
description = "Fast and small world format for Minestom"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.minestom)
    implementation(libs.zstd)
    // Fastutil is only included because minestom already uses it,
    // otherwise it is a crazy dependency for how it is used in this project.
    implementation(libs.fastutil)

    testImplementation("ch.qos.logback:logback-core:1.4.7")
    testImplementation("ch.qos.logback:logback-classic:1.4.7")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.minestom)
}

java {
    withSourcesJar()
    withJavadocJar()

    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.test {
    maxHeapSize = "2g"
    useJUnitPlatform()
}

nmcpAggregation {
    centralPortal {
        username = System.getenv("SONATYPE_USERNAME")
        password = System.getenv("SONATYPE_PASSWORD")
        publishingType = if ("dev" in project.version.toString()) "USER_MANAGED" else "AUTOMATIC"
    }

    publishAllProjectsProbablyBreakingProjectIsolation()
}

publishing.publications.create<MavenPublication>("maven") {
    groupId = "dev.hollowcube"
    artifactId = "polar"
    version = project.version.toString()

    from(project.components["java"])

    pom {
        name.set(artifactId)
        description.set(project.description)
        url.set("https://github.com/hollow-cube/polar")

        licenses {
            license {
                name.set("MIT")
                url.set("https://github.com/hollow-cube/polar/blob/main/LICENSE")
            }
        }

        developers {
            developer {
                id.set("mworzala")
                name.set("Matt Worzala")
                email.set("matt@hollowcube.dev")
            }
        }

        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/hollow-cube/polar/issues")
        }

        scm {
            connection.set("scm:git:git://github.com/hollow-cube/polar.git")
            developerConnection.set("scm:git:git@github.com:hollow-cube/polar.git")
            url.set("https://github.com/hollow-cube/polar")
            tag.set(System.getenv("TAG_VERSION") ?: "HEAD")
        }

        ciManagement {
            system.set("Github Actions")
            url.set("https://github.com/hollow-cube/polar/actions")
        }
    }
}

signing {
    isRequired = System.getenv("CI") != null

    val privateKey = System.getenv("GPG_PRIVATE_KEY")
    val keyPassphrase = System.getenv()["GPG_PASSPHRASE"]
    useInMemoryPgpKeys(privateKey, keyPassphrase)

    sign(publishing.publications)
}
