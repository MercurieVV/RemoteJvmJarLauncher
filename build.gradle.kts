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
    val javalinVersion = "5.4.2"   // any 5.x is fine
    val slf4jVersion = "2.0.16"

    implementation("org.pf4j:pf4j:$pf4jVersion")
    implementation("io.javalin:javalin:$javalinVersion")
    implementation("org.slf4j:slf4j-simple:$slf4jVersion")
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
    container {
        mainClass = "io.github.mercurievv.rjjl.Main"
        ports = listOf(System.getProperty("rjjl.port", "8080"))
        environment = mapOf(
            "PLUGINS_DIR" to System.getProperty("rjjl.pluginsDir", "/data/plugins"),
            "HTTP_PORT" to System.getProperty("rjjl.port", "8080")
        )
    }
}