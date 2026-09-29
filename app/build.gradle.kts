plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "de.example.nasphotosync"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.example.nasphotosync"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.3")
    implementation("androidx.navigation:navigation-compose:2.9.4")

    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    implementation("androidx.work:work-runtime-ktx:2.10.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // WebDAV-Client (selbst auf OkHttp aufgebaut, PROPFIND/PUT/GET/DELETE/MKCOL)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Verschluesselte Speicherung der NAS-Zugangsdaten (Android Keystore)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Vorschaubilder in Ordner-/Galerie-Ansichten
    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.compose.material:material-icons-extended:1.7.8")
}
