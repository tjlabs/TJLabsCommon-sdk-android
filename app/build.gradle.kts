import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
//        id("com.android.application")
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

val versionMajor = 1
val versionMinor = 0
val versionPatch = 0


android {
    namespace = "com.tjlabs.tjlabscommon_sdk_android"
    compileSdk = 34

    defaultConfig {
//        applicationId = "com.tjlabs.tjlabscommon_sdk_android"
//        versionCode = 1
//        versionName = "1.0"
        minSdk = 29
        targetSdk = 34


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    libraryVariants.all {
        outputs.all {
            (this as BaseVariantOutputImpl).outputFileName = "app-release-common.aar"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true // 중요
    }
}

dependencies {
    implementation (libs.opencsv)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.androidx.core.ktx.v131)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat.v120)
    implementation(libs.material.v120)
    implementation(libs.androidx.constraintlayout.v213)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit.v113)
    androidTestImplementation(libs.androidx.espresso.core.v340)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.tjlabs"
                artifactId = "TJLabsCommon-sdk-android"
                version = "$versionMajor.$versionMinor.$versionPatch"
            }
        }
    }
}
