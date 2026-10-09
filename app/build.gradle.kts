plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "de.galeriesync"
    compileSdk = 35
    defaultConfig { applicationId = "com.schiwy.galeriesync"; minSdk = 26; targetSdk = 35; versionCode = 10; versionName = "0.4.4" }
    signingConfigs {
        getByName("debug") {
            storeFile = file(System.getProperty("user.home") + "/.android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("debug") }
        release { isMinifyEnabled = false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("eu.agno3.jcifs:jcifs-ng:2.1.10")
    implementation("com.wireguard.android:tunnel:1.0.20230706")
    implementation("androidx.media3:media3-common:1.8.0")
    implementation("androidx.media3:media3-effect:1.8.0")
    implementation("androidx.media3:media3-transformer:1.8.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}

