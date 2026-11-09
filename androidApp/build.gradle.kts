import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.koin.android)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// Release signing comes from keystore.properties (not committed) or DATADROID_* environment variables,
// e.g. in a release pipeline. Without either, the release build is left unsigned, as on CI.
// Read through providers so the configuration cache notices when they change.
val keystoreProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText.orNull
        ?.let { load(StringReader(it)) }
}
fun signingValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: providers.environmentVariable(env).orNull
val releaseStoreFile = signingValue("storeFile", "DATADROID_KEYSTORE_FILE")

android {
    namespace = "org.onedroid.dataset"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.onedroid.dataset"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Set per release in gradle.properties, or with -PappVersionCode=... on CI.
        versionCode = providers.gradleProperty("appVersionCode").get().toInt()
        versionName = providers.gradleProperty("appVersionName").get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = signingValue("storePassword", "DATADROID_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "DATADROID_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "DATADROID_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // R8 makes a large difference to Compose's runtime performance.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}