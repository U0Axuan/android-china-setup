plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.u0axuan.androidchinasetup"
    compileSdk = 35
    defaultConfig {
        applicationId = "io.github.u0axuan.androidchinasetup"
        minSdk = 30
        targetSdk = 35
        versionCode = 6
        versionName = "0.6.0"
    }
    // Optional stable release signing. Credentials and keystore are CI secrets,
    // never checked into the public repository.
    signingConfigs {
        val keyFile = System.getenv("ANDROID_SIGNING_STORE_FILE")
        if (!keyFile.isNullOrBlank()) {
            create("stableRelease") {
                storeFile = file(keyFile)
                storePassword = System.getenv("ANDROID_SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_SIGNING_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (!System.getenv("ANDROID_SIGNING_STORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("stableRelease")
            }
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
