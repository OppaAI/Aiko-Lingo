plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

import java.util.Properties

// Single source of truth for the server URL: AIKO_PUBLIC_BASE_URL in
// Aiko-chan (~/.aiko/.env.age, or config yaml), synced into
// local.properties as aikoServerUrl=... by util/sync_app_server_url.sh.
// Rebuild the APK to repoint the app — there is no in-app URL editor.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val aikoServerUrl: String = (
    localProps.getProperty("aikoServerUrl")
        ?: System.getenv("AIKO_PUBLIC_BASE_URL")
        ?: "https://aiko.ide-chroma.ts.net/"
    ).trim().removeSuffix("/") + "/"

android {
    namespace = "com.aiko.lingo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aiko.lingo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.1"
        buildConfigField("String", "AIKO_SERVER_URL", "\"$aikoServerUrl\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.0")

    // Retrofit & Networking
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // Background work (hourly study reminders) + notifications
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.core:core-ktx:1.17.0")

    // Offline cache (framework SQLiteOpenHelper — no annotation processing)
    // + homescreen widget (Glance)
    implementation("androidx.glance:glance-appwidget:1.2.0")

    // Coil for images
    implementation("io.coil-kt.coil3:coil-compose:3.6.2")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
