import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

/**
 * Firma del release: keystore.properties en la raíz (NO se versiona, ver .gitignore) con storeFile y
 * keyAlias. Las contraseñas van ahí o en las variables de entorno CALC_STORE_PASSWORD y
 * CALC_KEY_PASSWORD. Sin ese archivo, assembleRelease produce un APK sin firmar.
 */
val firma: Properties? = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }

android {
    namespace = "co.com.calculadoradesalarios"
    compileSdk = 36

    defaultConfig {
        applicationId = "co.com.calculadoradesalarios"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (firma != null) {
            create("release") {
                storeFile = file(firma.getProperty("storeFile") ?: error("keystore.properties: falta storeFile"))
                keyAlias = firma.getProperty("keyAlias") ?: error("keystore.properties: falta keyAlias")
                storePassword = firma.getProperty("storePassword") ?: System.getenv("CALC_STORE_PASSWORD")
                    ?: error("Falta la contraseña del keystore (storePassword o CALC_STORE_PASSWORD)")
                keyPassword = firma.getProperty("keyPassword") ?: System.getenv("CALC_KEY_PASSWORD")
                    ?: storePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (firma != null) signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        // Los mismos data.json de prueba (sintéticos) que usa el motor y las referencias generadas con el C#.
        getByName("test").resources.srcDir("../engine/src/test/resources")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric 4.17 con SDK 36 accede a FileDescriptor internamente.
            it.jvmArgs(
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            )
        }
    }
}

dependencies {
    implementation(project(":engine"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.windowsizeclass)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
