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

## Unified Error Codes

`TJLabsErrorCodeManager`를 통해 통합 에러코드를 조회할 수 있습니다.

### Usage

```kotlin
import com.tjlabs.tjlabscommon_sdk_android.TJLabsErrorCodeManager
import com.tjlabs.tjlabscommon_sdk_android.TJLabsErrorDomain

val e1 = TJLabsErrorCodeManager.fromCode(1600)
// PERMISSION_DENIED

val e2 = TJLabsErrorCodeManager.fromName("ROUTE_NOT_FOUND")
// NAVI 1802

val e3 = TJLabsErrorCodeManager.fromNameNormalized("RESOURCE_NODE_LINK_ERROR")
// legacy name -> normalized mapping

val resourceErrors = TJLabsErrorCodeManager.getByDomain(TJLabsErrorDomain.RESOURCE)
val formatted = TJLabsErrorCodeManager.format(1903)
// [RESOURCE] 1903 RESOURCE_IMAGE_ERROR - 이미지 로드 실패

val recommended = TJLabsErrorCodeManager.getRecommendedSpecificCodes(1905)
// 1905 legacy aggregate -> [1913, 1914]
```

### Domain Codes

<details>
<summary>COMMON</summary>

| Code | Name | Meaning |
| ---: | --- | --- |
| 1000 | UNKNOWN | 알 수 없는 오류/예외 |

</details>

<details>
<summary>AUTH</summary>

| Code | Name | Meaning |
| ---: | --- | --- |
| 1100 | AUTH_FAILED | 인증/토큰 발급·검증 전체 실패 |
| 1101 | CREDENTIALS_MISSING | accessKey/secret/clientSecret 누락 |
| 1102 | TOKEN_REFRESH_FAILED | 토큰 갱신 실패 |
| 1103 | NOT_AUTHORIZED | 인증되지 않은 상태 |
| 1104 | LOGIN_FAILED | 로그인 API 실패 |

</details>

<details>
<summary>INIT</summary>

| Code | Name | Meaning |
| ---: | --- | --- |
| 1200 | INVALID_ID | 유효하지 않은 ID |
| 1201 | INVALID_MODE | 유효하지 않은 모드 |
| 1202 | RESOURCE_LOAD_FAILED | 리소스/초기 데이터 로드 실패 |
| 1203 | CALC_INIT_FAIL | Calc/Engine 초기화 단계 실패 |
| 1204 | INVALID_PARAMETER | 유효하지 않은 파라미터 |

</details>

<details>
<summary>SERVICE</summary>

| Code | Name | Meaning |
| ---: | --- | --- |
| 1300 | NOT_INITIALIZED | 초기화 없이 시작 요청 |
| 1301 | DUPLICATED_SERVICE | 이미 시작된 서비스 재시작 |
| 1302 | SERVICE_STOPPED | 서비스 중지 상태 |
| 1303 | SERVICE_ALREADY_STOPPED | 이미 중지된 서비스에 중지 요청 |

</details>

<details>
<summary>NETWORK</summary>

| Code | Name | Meaning |
| ---: | --- | --- |
| 1400 | NETWORK_DISCONNECT | 네트워크 단절 |
| 1401 | HTTP_4XX | 요청 파라미터/권한 등 클라이언트 오류 |
| 1402 | HTTP_5XX | 서버 내부 오류 |
| 1403 | NETWORK_TIMEOUT | 네트워크 타임아웃 |
| 1404 | HTTP_401_UNAUTHORIZED | 인증 실패(401) |
| 1405 | HTTP_403_FORBIDDEN | 권한 없음(403) |
| 1406 | HTTP_404_NOT_FOUND | 리소스 없음(404) |

</details>

<details>
<summary>GENERATOR</summary>

| Code | Name | Meaning |
| ---: | --- | --- |
| 1500 | GENERATOR_FAIL | 위치/엔진 런타임 동작 실패 |
| 1501 | GENERATOR_PRECHECK_FAIL | 엔진 사전점검 실패 |
| 1502 | SIMULATION_DATA_LOAD_FAIL | 시뮬레이션 데이터 로드 실패 |
| 1503 | SIMULATION_INVALID_FORMAT | 시뮬레이션 데이터 형식 오류 |

</details>

<details>
<summary>PERMISSION/BLE</summary>

| Code | Name | Meaning |
| ---: | --- | --- |
| 1600 | PERMISSION_DENIED | 권한 거부 |
| 1601 | BLUETOOTH_OFF | 블루투스 비활성화 |
| 1602 | BLUETOOTH_UNAVAILABLE | BLE 미지원/사용 불가 |
| 1603 | BLE_SCAN_STOP | BLE 스캔 중단/타임아웃 |
| 1604 | DUPLICATE_SCAN_START | BLE 스캔 중복 시작 |

</details>

<details>
<summary>VM</summary>

| Code | Name | Meaning |
| ---: | --- | --- |
| 1700 | WEBVIEW_INIT_FAIL | WebView/Bridge 초기화 실패 |
| 1701 | VM_VIEW_FAIL | VM View 초기화 실패 |

</details>

<details>
<summary>NAVI</summary>

| Code | Name | Meaning |
| ---: | --- | --- |
| 1800 | ROUTE_REQUEST_FAILED | 경로 요청 실패 |
| 1801 | ROUTE_GUIDANCE_OUT | 경로 이탈 |
| 1802 | ROUTE_NOT_FOUND | 경로 없음 |
| 1803 | NAVIGATION_ROUTE_FAILED | 내부 경로 생성 실패 |

</details>

<details>
<summary>RESOURCE</summary>

| Code | Name | Meaning |
| ---: | --- | --- |
| 1900 | RESOURCE_DOMAIN_ERROR | 리소스 도메인 일반 실패 |
| 1901 | RESOURCE_SECTOR_ERROR | Sector 데이터 실패 |
| 1902 | RESOURCE_PATH_PIXEL_ERROR | PathPixel 데이터 실패 |
| 1903 | RESOURCE_IMAGE_ERROR | 이미지 로드 실패 |
| 1904 | RESOURCE_AFFINE_ERROR | Affine 데이터 실패 |
| 1905 | RESOURCE_NODE_LINK_ERROR | Node/Link 통합 실패(legacy aggregate) |
| 1906 | RESOURCE_SCALE_ERROR | Scale 데이터 실패 |
| 1907 | RESOURCE_ENTRANCE_ERROR | Entrance 데이터 실패 |
| 1908 | RESOURCE_LEVEL_UNITS_ERROR | LevelUnits 데이터 실패 |
| 1909 | RESOURCE_PARAM_ERROR | 파라미터 데이터 실패 |
| 1910 | RESOURCE_GEOFENCE_ERROR | Geofence 데이터 실패 |
| 1911 | RESOURCE_BUILDING_LEVEL_ERROR | Building/Level 데이터 실패 |
| 1912 | RESOURCE_LEVEL_WARDS_ERROR | LevelWards 데이터 실패 |
| 1913 | RESOURCE_NODE_ERROR | Node 데이터 실패 |
| 1914 | RESOURCE_LINK_ERROR | Link 데이터 실패 |
| 1915 | RESOURCE_LANDMARK_ERROR | Landmark 데이터 실패 |
| 1916 | RESOURCE_SPOTS_ERROR | Spots 데이터 실패 |

</details>
