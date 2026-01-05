plugins {
    id("java")
    application
    id("com.google.cloud.tools.jib") version "3.5.1"
}

group = "org.example"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}
application {
    mainClass.set("com.example.host.App")
}

repositories {
    mavenCentral()
}

dependencies {
    val pf4jVersion = "3.14.0"
    val javalinVersion = "6.7.0"   // any 5.x is fine
    val slf4jVersion = "2.0.17"

    implementation("org.pf4j:pf4j:$pf4jVersion")
    implementation("io.javalin:javalin:$javalinVersion")
    implementation("org.slf4j:slf4j-simple:$slf4jVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.20.1")
}

jib {
    from {
        image = "eclipse-temurin:24-jre"
        platforms {
            platform {
                architecture = "arm64"
                os = "linux"
            }
            platform {
                architecture = "amd64"
                os = "linux"
            }
        }
    }
    to {
        image = "ghcr.io/mercurievv/remotejvmjarlauncher:latest"
    }
    val internalPort = System.getProperty("rjjl.internalPort", "8666")
    val externalPort = System.getProperty("rjjl.externalPort", "8777")
    container {
        mainClass = "io.github.mercurievv.rjjl.Main"
        ports = listOf(internalPort, externalPort)
        environment = mapOf(
            "PLUGINS_DIR" to System.getProperty("rjjl.pluginsDir", "/data/plugins"),
            "INTERNAL_HTTP_PORT" to internalPort,
            "EXTERNAL_HTTP_PORT" to externalPort,
        )
    }
}
/*
sourceSets {
    create("intTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val intTestImplementation by configurations.getting {
    extendsFrom(configurations.implementation.get())
}
val intTestRuntimeOnly by configurations.getting

configurations["intTestRuntimeOnly"].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    intTestImplementation("org.junit.jupiter:junit-jupiter:5.7.1")
    intTestRuntimeOnly("org.junit.platform:junit-platform-launcher")
}*/
