# TJLabsCommon-sdk-android

Android Common SDK for RFD/UVD generation and simulation playback.

## Requirements

### Consumer App
- Kotlin
- Android API 26+

### SDK Build/Release (this repository)
- JDK 17
- AGP 8+ (current: 8.6.0)
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

## CI/CD Automation

### PR Validation Workflow
- File: `.github/workflows/pr-validate.yml`
- Triggers:
  - `pull_request` to `main`, `release/*`
  - `workflow_dispatch`
- Validation steps:
  - JDK 17 setup
  - `:sdk:testDebugUnitTest`
  - `:sdk:testReleaseUnitTest`
  - `:sdk:publishToMavenLocal -x test`
- Any failure makes the workflow fail (usable as required status check for merge gate).

### Release Automation Workflow
- File: `.github/workflows/release-jitpack.yml`
- Triggers:
  - `push` to `release/*`
  - `workflow_dispatch` with optional `release_version`
- Automation steps:
  - Resolve release version (`workflow input` first, fallback from `release/x.y.z` branch)
  - Validate version format (`x.y.z`)
  - Verify module version in `sdk/build.gradle.kts` matches release version
  - Run SDK unit tests + publish check
  - Create/push Git tag (`x.y.z`) if missing
  - Warm up JitPack build endpoint and upload build-log artifacts

### Exclusions
- Live API smoke tests are intentionally excluded from automated workflows.

## Required Permissions

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

## Optional SDK Logs

SDK internal logs are disabled by default. You can enable or disable them at runtime:

```kotlin
import com.tjlabs.tjlabscommon_sdk_android.utils.TJLabsCommonLog

TJLabsCommonLog.setEnabled(true)  // enable
TJLabsCommonLog.setEnabled(false) // disable
```
