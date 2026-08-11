import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun gitVersionTag(): String {
    return try {
        val process = ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val tag = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        if (process.exitValue() != 0 || tag.isBlank() || tag.contains("致命") || tag.contains("fatal")) {
            "1.0.0"
        } else {
            tag.removePrefix("v")
        }
    } catch (e: Exception) {
        "1.0.0"
    }
}

val versionTag = gitVersionTag()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

android {
    namespace = "online.youcd.heartrate"
    compileSdk = 36

    defaultConfig {
        applicationId = "online.youcd.heartrate"
        minSdk = 31
        targetSdk = 36
        versionCode = run {
            val parts = versionTag.split(".").map { it.toIntOrNull() ?: 0 }
            (parts.getOrElse(0) { 0 } * 10000) +
                (parts.getOrElse(1) { 0 } * 100) +
                parts.getOrElse(2) { 0 }
        }.coerceAtLeast(1)
        versionName = versionTag
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String?
                keyPassword = keystoreProperties["keyPassword"] as String?
                storeFile = keystoreProperties["storeFile"]?.let { file(it) }
                storePassword = keystoreProperties["storePassword"] as String?
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    compileOnly(libs.errorprone.annotations)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
}

tasks.withType<JavaCompile>().configureEach {
    if (name.startsWith("hiltJavaCompile")) {
        val variant = name.removePrefix("hiltJavaCompile").lowercase()
        val kspJava = layout.buildDirectory.dir("generated/ksp/$variant/java")
        source(kspJava)
        options.sourcepath = files(kspJava)
    }
}
