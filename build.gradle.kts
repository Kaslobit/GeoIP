plugins {
    kotlin("jvm") version "2.1.20"
    id("com.gradleup.shadow") version "8.3.6"
    application
}

dependencies {
    // Ktor core + Netty server
    implementation("io.ktor:ktor-server-core-jvm:3.1.2")
    implementation("io.ktor:ktor-server-netty-jvm:3.1.2")

    // JSON serialization with Jackson
    implementation("io.ktor:ktor-server-content-negotiation:3.1.2")
    implementation("io.ktor:ktor-serialization-jackson:3.1.2")

    // YAML parsing
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.3")

    // Simple logs
    implementation("org.slf4j:slf4j-simple:2.0.17")

    // MaxMind GeoIP2 DB access
    implementation("com.maxmind.geoip2:geoip2:4.2.1")
}

application {
    mainClass.set("me.kaslo.geoip.MainKt")
}

group = "me.kaslo.geoip"
version = "1.2"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

tasks {
    build {
        dependsOn("shadowJar")
    }
    shadowJar {
        archiveClassifier.set("")
    }
    jar {
        enabled = false
    }
    shadowDistZip {
        enabled = false
    }
    shadowDistTar {
        enabled = false
    }
    startShadowScripts {
        enabled = false
    }
    startScripts {
        enabled = false
    }
    distTar {
        enabled = false
    }
    distZip {
        enabled = false
    }
}
