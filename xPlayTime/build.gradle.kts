plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.2.2"
}

group = "dev.xplaytime"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:4.2.0")

    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.6")
    implementation("org.yaml:snakeyaml:2.5")
}

kotlin {
    // Toolchain 25 so Gradle accepts velocity-api 4.2 (requires JVM 25+).
    // Kotlin 2.2 falls back to emitting JVM 24 bytecode, which runs on Java 25.
    jvmToolchain(25)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks {
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filesMatching("velocity-plugin.json") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("xPlayTime-${project.version}.jar")
        relocate("com.zaxxer.hikari", "dev.xplaytime.libs.hikari")
        // Relocate driver + rewrite string constants / service files for DriverManager.
        relocate("org.mariadb", "dev.xplaytime.libs.mariadb")
        relocate("org.yaml.snakeyaml", "dev.xplaytime.libs.snakeyaml")
        mergeServiceFiles()
    }

    build {
        dependsOn(shadowJar)
    }

    jar {
        enabled = false
    }
}
