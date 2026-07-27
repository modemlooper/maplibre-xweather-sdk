import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.library) apply false
}

// Xweather credentials are kept out of version control. Provide them via
// local.properties (gitignored) or environment variables:
//   xweather.clientId / XWEATHER_CLIENT_ID
//   xweather.clientSecret / XWEATHER_CLIENT_SECRET
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun credential(propertyKey: String, envKey: String): String =
    (localProperties.getProperty(propertyKey) ?: System.getenv(envKey) ?: "").also {
        if (it.isEmpty()) {
            logger.warn("Xweather credential '$propertyKey' is not set; see local.properties.")
        }
    }

android {
    namespace = "com.xweather.maplibre.sample"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xweather.maplibre.sample"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        resValue("string", "xweather_client_id", credential("xweather.clientId", "XWEATHER_CLIENT_ID"))
        resValue("string", "xweather_client_secret", credential("xweather.clientSecret", "XWEATHER_CLIENT_SECRET"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Local module reference for now; once published, consumers would instead
    // depend on `com.github.<org>:xweather-webview:<tag>` via JitPack.
    implementation(project(":xweather-webview"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // Compose (for @Preview of custom Views)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
