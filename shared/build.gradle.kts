import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// TensorFlow Lite C runtime for iOS, same archive the official TensorFlowLiteC CocoaPod uses.
val tfliteCVersion = "2.17.0"
val tfliteCUrl = "https://dl.google.com/tflite-release/ios/prod/tensorflow/lite/release/ios/release/32/" +
    "20240729-115310/TensorFlowLiteC/2.17.0/0c10b3543e01f547/TensorFlowLiteC-2.17.0.tar.gz"
val tfliteCDir = rootProject.layout.projectDirectory.dir("third_party/tflite-c/$tfliteCVersion")
val tfliteXcframework = tfliteCDir.dir("TensorFlowLiteC-$tfliteCVersion/Frameworks/TensorFlowLiteC.xcframework")

val downloadTfliteC = tasks.register("downloadTfliteC") {
    val url = tfliteCUrl
    val archive = tfliteCDir.file("TensorFlowLiteC-$tfliteCVersion.tar.gz").asFile
    val marker = tfliteXcframework.file("Info.plist").asFile
    outputs.dir(tfliteCDir)
    onlyIf { !marker.exists() }
    doLast {
        archive.parentFile.mkdirs()
        URI(url).toURL().openStream().use { input -> archive.outputStream().use { input.copyTo(it) } }
        val tar = ProcessBuilder("tar", "xzf", archive.absolutePath, "-C", archive.parentFile.absolutePath).inheritIO().start()
        check(tar.waitFor() == 0) { "Failed to extract $archive" }
        archive.delete()
    }
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        val slice = if (iosTarget.name == "iosArm64") "ios-arm64" else "ios-arm64_x86_64-simulator"
        val frameworkDir = tfliteXcframework.dir(slice).asFile.absolutePath
        iosTarget.compilations.getByName("main").cinterops.create("tflite") {
            definitionFile = project.file("src/nativeInterop/cinterop/tflite.def")
            compilerOpts("-I$frameworkDir/TensorFlowLiteC.framework/Headers")
            tasks.named(interopProcessingTaskName) { dependsOn(downloadTfliteC) }
        }
        // Test executables link here; the app links TensorFlowLiteC via iosApp/Configuration/Config.xcconfig.
        iosTarget.binaries.all {
            linkerOpts("-F$frameworkDir", "-framework", "TensorFlowLiteC", "-lc++")
        }
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    android {
        namespace = "org.onedroid.dataset.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.litert)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.exifinterface)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.compose)
            implementation(libs.koin.android)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.io.core)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation.compose)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.testExt.junit)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

compose.resources {
    packageOfResClass = "org.onedroid.dataset.resources"
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
