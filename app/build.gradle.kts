import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    kotlin("plugin.serialization") version "2.2.10"
}

// Load local.properties manually (this is a plain Properties file, not
// Gradle's default one, so it needs an explicit read) — this is what lets
// SUPABASE_URL/SUPABASE_ANON_KEY live outside version control while still
// being available at build time.
//
// NOTE: must import java.util.Properties explicitly (see above) rather
// than writing java.util.Properties() inline — AGP injects a Project
// extension literally named `java` into this script's scope, which
// shadows the `java` package name and breaks the fully-qualified path.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { stream -> load(stream) }
    }
}

android {
    namespace = "com.storagerush.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.storagerush.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Missing values fail the build loudly rather than silently
        // shipping a blank/broken Supabase client — better to catch a
        // missing local.properties entry at build time than at runtime.
        val supabaseUrl = localProperties.getProperty("supabase.url")
            ?: error("Missing supabase.url in local.properties")
        val supabaseAnonKey = localProperties.getProperty("supabase.anonKey")
            ?: error("Missing supabase.anonKey in local.properties")

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Enable Java 8+ APIs (java.time) on minSdk 24
    // Also required by supabase-kt, which targets minSdk 26 —
    // desugaring is what lets it run down to our minSdk 24.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Standard Jetpack Compose & Core Dependencies
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Image Loading (Coil for Jetpack Compose)
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Video frame thumbnail decoding for Coil (fixes blank video cards in the deck)
    implementation("io.coil-kt:coil-video:2.6.0")

    // Media3 ExoPlayer for the video overlay player (Phase B)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // DataStore Preferences (for local app settings)
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Lifecycle & ViewModel for Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Kotlin Serialization (for trash persistence)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // --- Supabase (Phase A/B: cloud sync + friends leaderboard) ---
    // BOM aligns versions across all supabase-kt modules below
    implementation(platform("io.github.jan-tennert.supabase:bom:3.5.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")

    // Ktor HTTP engine for Android — required by supabase-kt to make network calls
    implementation("io.ktor:ktor-client-android:3.1.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}