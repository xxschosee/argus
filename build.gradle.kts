plugins {
    java
}

group = "dev.argus"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

tasks {
    processResources {
        filesMatching(listOf("plugin.yml")) {
            expand("version" to project.version)
        }
    }
    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

// Prints (and downloads) the resolved compile classpath - handy for API archaeology.
tasks.register("resolveAll") {
    notCompatibleWithConfigurationCache("prints files at execution time")
    doLast {
        configurations.compileClasspath.get().resolvedConfiguration.resolvedArtifacts.forEach {
            println(it.file.absolutePath)
        }
    }
}
