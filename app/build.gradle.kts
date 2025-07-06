plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

android {
    namespace = "at.plankt0n.streamplay"
    compileSdk = 35

    defaultConfig {
        applicationId = "at.plankt0n.streamplay"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation("com.squareup.okhttp3:okhttp:4.12.0")




}