import java.util.Calendar

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

// Git Commit Hash für Build-Identifikation
fun getGitHash(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        "unknown"
    }
}

// Build Timestamp (ändert sich bei jedem Build)
fun getBuildTimestamp(): String {
    val cal = Calendar.getInstance()
    val year = (cal.get(Calendar.YEAR) % 100).toString().padStart(2, '0')
    val month = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
    val day = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    val hour = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val min = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    return "$year$month$day-$hour$min"
}

android {
    namespace = "at.plankt0n.streamplay"
    compileSdk = 35

    defaultConfig {
        applicationId = "at.plankt0n.streamplay"
        minSdk = 29
        targetSdk = 35
        versionCode = 2
        versionName = "1.2.3"

        // Build Hash und Timestamp in BuildConfig verfügbar machen
        buildConfigField("String", "GIT_HASH", "\"${getGitHash()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTimestamp()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("Boolean", "ENABLE_SELF_UPDATE", "true")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("Boolean", "ENABLE_SELF_UPDATE", "false")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.lifecycle.process)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.tbuonomo:dotsindicator:5.1.0")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.media:media:1.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.8.0")

        // Media3 (aktuellste stabile Version 1.3.0 im Mai 2025 – Stand: 2025-05)
        val mediaVersion = "1.6.1"
        implementation("androidx.media3:media3-exoplayer:$mediaVersion")
        implementation("androidx.media3:media3-exoplayer-hls:$mediaVersion")
        implementation("androidx.media3:media3-session:$mediaVersion")
        implementation("androidx.media3:media3-common:$mediaVersion")

        // Navigation
        val navigationVersion = "2.8.0"
        implementation("androidx.navigation:navigation-fragment-ktx:$navigationVersion")
        implementation("androidx.navigation:navigation-ui-ktx:$navigationVersion")

        // WorkManager
        implementation("androidx.work:work-runtime-ktx:2.9.0")

        // Gson
        implementation("com.google.code.gson:gson:2.10.1")
    //Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("jp.wasabeef:glide-transformations:4.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")




}