plugins {
    id("java")
}

group = "io.github.tanguygab"
version = "1.1.0"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/groups/public/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:26.1-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-all:5.0.0.Alpha2")
    compileOnly("it.unimi.dsi:fastutil:8.5.18")
    compileOnly(files("../../dependencies/nms/nms-26.1.jar"))
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
