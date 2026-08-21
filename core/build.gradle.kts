plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.miolauncher.core"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        targetSdk = 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            resources.srcDirs("src/main/resources")
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // HMCLCore dependencies
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.glavo.kala:kala-compress-archivers-zip:1.27.1-3")
    implementation("org.glavo.kala:kala-compress-archivers-tar:1.27.1-3")
    implementation("org.glavo:kala-encoding-detector:0.1.0")
    implementation("org.glavo:simple-png-javafx:0.3.0")
    implementation("org.tomlj:tomlj:1.1.1")
    implementation("org.tukaani:xz:1.12")
    implementation("org.glavo:lz4-java:1.10.4.1")
    implementation("org.hildan.fxgson:fx-gson:5.0.0")
    implementation("org.jenkins-ci:constant-pool-scanner:1.2")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("net.java.dev.jna:jna:5.18.1")
    implementation("org.glavo:pci-ids:0.4.0")
    implementation("org.glavo:HelloNBT:0.4.0")
    implementation("org.glavo:weburl:0.2.0")
    implementation("org.glavo:uuid-tools:0.2.0")
    implementation("org.commonmark:commonmark:0.27.1")
    implementation("org.commonmark:commonmark-ext-autolink:0.27.1")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.27.1")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.27.1")
    implementation("org.commonmark:commonmark-ext-ins:0.27.1")
    compileOnly("org.jetbrains:annotations:24.1.0")
    implementation("org.jetbrains:annotations:24.1.0")
}
