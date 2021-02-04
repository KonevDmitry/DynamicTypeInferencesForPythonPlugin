import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.changelog.closure
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
//        maven("https://oss.sonatype.org/content/repositories/snapshots/")
//        maven("https://dl.bintray.com/jetbrains/intellij-plugin-service")
    }
}
plugins {
    // Java support
    id("java")

    // Kotlin support
    id("org.jetbrains.kotlin.jvm") version "1.4.0"
    // gradle-intellij-plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
    id("org.jetbrains.intellij") version "0.6.5"
    // gradle-changelog-plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
    id("org.jetbrains.changelog") version "0.4.0"
    // detekt linter - read more: https://detekt.github.io/detekt/kotlindsl.html
    id("io.gitlab.arturbosch.detekt") version "1.11.0"
}

// Import variables from gradle.properties file
val pluginGroup: String by project
val pluginName: String by project
val pluginVersion: String by project
val pluginSinceBuild: String by project
val pluginUntilBuild: String by project

val platformType: String by project
val platformVersion: String by project
val platformDownloadSources: String by project
val pythonVersion: String by project
val psiViewerVersion: String by project
val env: String? = System.getenv("CONNOTATOR_ENV")

group = pluginGroup
version = pluginVersion

repositories {
    mavenCentral()
    jcenter()
    maven("https://repo.gradle.org/gradle/libs-releases-local/")
    maven("https://jetbrains.bintray.com/intellij-third-party-dependencies")
}

dependencies {
    implementation("org.junit.jupiter:junit-jupiter:5.4.2")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.11.0")

    testImplementation(kotlin("test-junit"))
    implementation("org.apache.commons:commons-csv:1.8")
    implementation("org.bytedeco:javacpp:1.5.4")
    implementation("org.bytedeco:javacpp-presets:1.5.4")
    implementation("org.slf4j:slf4j-api:1.7.26")
    implementation("org.slf4j:slf4j-simple:1.7.26")
    implementation("net.java.dev.jna:jna:5.3.0")

    implementation("ai.djl:api:0.9.0")
    implementation("ai.djl:basicdataset:0.9.0")

    // MXNet
//    implementation("ai.djl.mxnet:mxnet-model-zoo:0.9.0")
//    implementation("ai.djl.mxnet:mxnet-native-auto:1.7.0-backport")

    // TF config
    implementation("ai.djl.tensorflow:tensorflow-api:0.9.0")
    implementation("ai.djl.tensorflow:tensorflow-engine:0.9.0")
    implementation("ai.djl.tensorflow:tensorflow-model-zoo:0.9.0")
    implementation("ai.djl.tensorflow:tensorflow-native-auto:2.3.1")

//  PyTorch and MXNet work in the same way but on my PC lib for PyTorch crashes, so...
//  Let it be here just because
//
//    implementation("ai.djl.pytorch:pytorch-model-zoo:0.9.0")
//    implementation("ai.djl.pytorch:pytorch-native-auto:1.7.0")


}

intellij {
    pluginName = pluginName
    version = platformVersion
    type = platformType
    downloadSources = platformDownloadSources.toBoolean()
    updateSinceUntilBuild = true
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "1.8"
        targetCompatibility = "1.8"
    }
    listOf("compileKotlin", "compileTestKotlin").forEach {
        getByName<KotlinCompile>(it) {
            kotlinOptions.jvmTarget = "1.8"
        }
    }

    withType<Detekt> {
        jvmTarget = "1.8"
    }

    patchPluginXml {
        version(pluginVersion)
        sinceBuild(pluginSinceBuild)
        untilBuild(pluginUntilBuild)

        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        pluginDescription(
            closure {
                File("./README.md").readText().lines().run {
                    val start = "<!-- Plugin description -->"
                    val end = "<!-- Plugin description end -->"
                    if (!containsAll(listOf(start, end))) {
                        throw GradleException("Plugin description section not found in README.md file:\n$start ... $end")
                    }
                    subList(indexOf(start) + 1, indexOf(end))
                }.joinToString("\n").run { markdownToHTML(this) }
            }
        )

        // Get the latest available change notes from the changelog file
        changeNotes(
            closure {
                changelog.getLatest().toHTML()
            }
        )
    }

    publishPlugin {
        dependsOn("patchChangelog")
        token(System.getenv("PUBLISH_TOKEN"))
        channels(pluginVersion.split('-').getOrElse(1) { "default" }.split('.').first())
    }
}
