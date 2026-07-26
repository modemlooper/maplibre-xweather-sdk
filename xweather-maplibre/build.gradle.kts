plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.xweather.maplibre"
    compileSdk = 35

    defaultConfig {
        // org.maplibre.gl:android-sdk:13.4.1's manifest requires minSdk 23.
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(libs.maplibre.android.sdk)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}

// Publishes to JitPack: a GitHub release tag is all JitPack needs to build
// this coordinate as com.github.<org>:xweather-maplibre-sdk:<tag> — no
// credentials or repo URL required on our side.
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.xweather"
            artifactId = "xweather-maplibre"
            version = "0.1.0"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
