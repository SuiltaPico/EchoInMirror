import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.ExperimentalComposeLibrary
import java.net.URL
import java.net.HttpURLConnection
import java.io.FileOutputStream

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization") version "1.7.20"
}

group = "com.eimsound"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://s01.oss.sonatype.org/content/repositories/releases/")
}

kotlin {
    jvmToolchain(17)
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
        withJava()
    }
    sourceSets {
        @Suppress("UNUSED_VARIABLE", "OPT_IN_IS_NOT_ENABLED") val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs) {
                    exclude("org.jetbrains.compose.material")
                }
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
                implementation(compose.materialIconsExtended)
                @OptIn(ExperimentalComposeLibrary::class) implementation(compose.material3)
//                @OptIn(ExperimentalComposeLibrary::class) implementation(compose.desktop.components.splitPane)

                implementation("org.pf4j:pf4j:3.8.0")
                implementation("commons-io:commons-io:2.11.0")
                implementation("org.apache.commons:commons-lang3:3.12.0")
                implementation("org.slf4j:slf4j-simple:2.0.3")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "EchoInMirror"
            packageVersion = "1.0.0"
        }
    }
}

task<Copy>("downloadEIMHost") {
    val file = File("EIMHost.exe")
    if (file.exists()) {
        println("File exists, skipping download.")
        return@task
    }
    val connection = URL("https://github.com/EchoInMirror/EIMHost/releases/latest/download/EIMHost.exe").openConnection() as HttpURLConnection
    connection.connect()
    val input = connection.inputStream
    val output = FileOutputStream(file)
    input.copyTo(output)
    input.close()
    output.close()
}

// Run before build
tasks.withType<GradleBuild>() {
    dependsOn(":downloadEIMHost")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.languageVersion = "1.8"
}
