plugins {
    java
}

group = "com.xxschosee"
version = providers.gradleProperty("version").orElse("0.1.0-SNAPSHOT").get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
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
}
