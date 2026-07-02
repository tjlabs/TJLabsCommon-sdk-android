import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun String.sanitizeProp(): String {
    var v = trim()
    if (v.length >= 2) {
        val first = v.first()
        val last = v.last()
        // Strip a balanced wrapping quote, or a single leading/trailing stray quote
        if ((first == '"' || first == '\'') && first == last) {
            v = v.substring(1, v.length - 1)
        } else {
            if (first == '"' || first == '\'') v = v.substring(1)
            if (v.isNotEmpty() && (v.last() == '"' || v.last() == '\'')) v = v.substring(0, v.length - 1)
        }
    } else if (v.length == 1 && (v == "\"" || v == "'")) {
        v = ""
    }
    return v.trim()
}

fun String.ensureTrailingSlash(): String =
    if (isBlank() || endsWith("/")) this else "$this/"

fun findProp(vararg keys: String, default: String = ""): String {
    for (key in keys) {
        val gradleValue = project.findProperty(key)?.toString()?.sanitizeProp()
        if (!gradleValue.isNullOrBlank()) return gradleValue
        val localValue = localProperties.getProperty(key)?.sanitizeProp()
        if (!localValue.isNullOrBlank()) return localValue
    }
    return default
}

fun String.escapeForBuildConfig(): String = this
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.tjlabs.tjlabscommon_sample"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tjlabs.tjlabscommon_sample"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val authClientKey = findProp("AUTH_CLIENT_KEY", "client_key", "CLIENT_KEY")
        val authAccessKey = findProp("AUTH_ACCESS_KEY", "access_key", "ACCESS_KEY")
        val authSecretAccessKey = findProp("AUTH_SECRET_ACCESS_KEY", "secret_access_key", "SECRET_ACCESS_KEY")
        val userBaseUrl = findProp(
            "USER_BASE_URL",
            default = "https://asia-northeast3.user.jupiter.tjlabs.dev/"
        ).ensureTrailingSlash()
        val recBaseUrl = findProp(
            "REC_BASE_URL",
            default = "https://asia-northeast3.rec.jupiter.tjlabs.dev/"
        ).ensureTrailingSlash()

        buildConfigField("String", "AUTH_CLIENT_KEY", "\"${authClientKey.escapeForBuildConfig()}\"")
        buildConfigField("String", "AUTH_ACCESS_KEY", "\"${authAccessKey.escapeForBuildConfig()}\"")
        buildConfigField("String", "AUTH_SECRET_ACCESS_KEY", "\"${authSecretAccessKey.escapeForBuildConfig()}\"")
        buildConfigField("String", "USER_BASE_URL", "\"${userBaseUrl.escapeForBuildConfig()}\"")
        buildConfigField("String", "REC_BASE_URL", "\"${recBaseUrl.escapeForBuildConfig()}\"")
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(project(":sdk"))
    implementation("com.github.tjlabs:TJLabsAuth-sdk-android:1.0.26")
    implementation("com.github.tjlabs:TJLabsResource-sdk-android:1.1.7")

    implementation(libs.androidx.core.ktx.v131)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
