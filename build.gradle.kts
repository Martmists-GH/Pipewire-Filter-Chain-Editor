import org.jetbrains.compose.ComposeBuildConfig
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.compose") version "2.3.0"
    id("org.jetbrains.compose") version "1.10.0"
}

group = "com.martmists"
version = "1.0.1"

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("org.jetbrains.compose.ui:ui:${ComposeBuildConfig.composeVersion}")
    implementation("org.jetbrains.compose.runtime:runtime:${ComposeBuildConfig.composeVersion}")
    implementation("org.jetbrains.compose.foundation:foundation:${ComposeBuildConfig.composeVersion}")
    implementation("org.jetbrains.compose.components:components-resources:${ComposeBuildConfig.composeVersion}")
    implementation("org.jetbrains.compose.animation:animation:${ComposeBuildConfig.composeVersion}")
    implementation("org.jetbrains.compose.animation:animation-graphics:${ComposeBuildConfig.composeVersion}")
    implementation("org.jetbrains.compose.components:components-resources:${ComposeBuildConfig.composeVersion}")
    implementation("org.jetbrains.compose.material3:material3:${ComposeBuildConfig.composeMaterial3Version}")
    implementation(compose.desktop.currentOs)
    implementation("io.github.vinceglb:filekit-dialogs-compose:0.13.0")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21

            freeCompilerArgs.addAll(
                "-Xexplicit-backing-fields",
            )
        }
    }
}

compose {
    desktop {
        application {
            mainClass = "com.martmists.pipewire.editor.MainKt"

            nativeDistributions {
                targetFormats(TargetFormat.AppImage, TargetFormat.Deb)

                packageName = "Pipewire Filter Chain Editor"
                packageVersion = version.toString()
                licenseFile = file("LICENSE")

                linux {
                    modules("java.desktop", "jdk.unsupported", "jdk.security.auth")
                }
            }
        }
    }
}
