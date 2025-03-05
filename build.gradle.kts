/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Java library project to get you started.
 * For more details on building Java & JVM projects, please refer to https://docs.gradle.org/8.4/userguide/building_java_projects.html in the Gradle documentation.
 */
group = "jp.furplag.spigotmc.dynmap.extension"
version = "1.4.0"
description = "auto generate structure markers into Dynmap ."

val paperSpigotVersion = "1.21.4-R0.1-SNAPSHOT"
plugins {
  // Apply the java-library plugin for API and implementation separation.
  `java-library`
  id("com.diffplug.spotless") version "7.0.2"
  id("com.gradleup.shadow") version "8.3.0"  
}
repositories {
  mavenCentral()
  maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") /* Spigot API */
  maven("https://repo.mikeprimm.com/") /* Dynmap */
  maven("https://jitpack.io/") /* furplag/relic */
}

dependencies {
  compileOnly("org.spigotmc:spigot-api:${paperSpigotVersion}")
  compileOnly("us.dynmap:DynmapCoreAPI:3.7-beta-6")

  compileOnly("org.projectlombok:lombok:1.18.36")
  annotationProcessor("org.projectlombok:lombok:1.18.36")

  implementation("com.github.furplag:relic:5.1.0")
}

spotless {
  java {
    endWithNewline()
    leadingTabsToSpaces(2)
    removeUnusedImports()
    trimTrailingWhitespace()
  }
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
  compileJava {
    dependsOn(spotlessApply)
    options.encoding = Charsets.UTF_8.name()
    options.release.set(21)
  }
  javadoc {
    options.encoding = Charsets.UTF_8.name()
  }
  processResources {
    filteringCharset = Charsets.UTF_8.name()
    val props = mapOf(
      "name" to project.name,
      "version" to project.version,
      "description" to project.description,
      "apiVersion" to "1.21"
    )
    inputs.properties(props)
    filesMatching("plugin.yml") {
      expand(props)
    }
  }
  shadowJar {
    archiveBaseName.set("DynmapStructureMarkers")
    archiveAppendix.set("Spigot")
    archiveVersion.set("${version}")
    archiveClassifier.set("")
    dependencies {
      include(dependency("com.github.furplag:relic:"))
    }
    relocate("jp.furplag.sandbox", "jp.furplag.sandbox.relic")
  }
}

tasks.named("jar") {
  finalizedBy("shadowJar")
}
