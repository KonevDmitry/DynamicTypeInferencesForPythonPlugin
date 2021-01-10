import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm")
}

group = "me.dmitry"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
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

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}