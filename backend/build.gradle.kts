plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.miolauncher.backend"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    implementation(project(":core"))
    implementation("org.tukaani:xz:1.12")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.code.gson:gson:2.11.0")
    // FCL 的 lwjgl.jar：让 ART 能 FindClass org.lwjgl.glfw.CallbackBridge（JNI_OnLoad 需要）
    compileOnly(files("src/main/assets/runtime/lwjgl.jar"))
}
