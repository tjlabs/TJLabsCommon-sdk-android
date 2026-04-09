# TJLabsCommon-sdk-android

Android Common SDK for RFD/UVD generation and simulation playback.

## Requirements

### Consumer App
- Kotlin
- Android API 26+

### SDK Build/Release (this repository)
- JDK 17 (AGP 8.6.0)
- Gradle 8.7 (wrapper)

## Installation (JitPack)

### 1) Add JitPack repository

`settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

### 2) Add dependency

`app/build.gradle.kts`

```kotlin
dependencies {
    implementation("com.github.tjlabs:TJLabsCommon-sdk-android:<tag>")
}
```

- Replace `<tag>` with your release tag.
- This repository is multi-module, but consumers should use the `:sdk` artifact only.

## Required Permissions

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" android:maxSdkVersion="30" />
```
