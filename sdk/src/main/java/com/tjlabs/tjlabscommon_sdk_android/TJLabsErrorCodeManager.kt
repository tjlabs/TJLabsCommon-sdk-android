package com.tjlabs.tjlabscommon_sdk_android

enum class TJLabsErrorDomain(val displayName: String) {
    COMMON("COMMON"),
    AUTH("AUTH"),
    INIT("INIT"),
    SERVICE("SERVICE"),
    NETWORK("NETWORK"),
    GENERATOR("GENERATOR"),
    PERMISSION_BLE("PERMISSION/BLE"),
    VM("VM"),
    NAVI("NAVI"),
    RESOURCE("RESOURCE")
}

data class TJLabsErrorCode(
    val domain: TJLabsErrorDomain,
    val code: Int,
    val name: String,
    val meaning: String
)

object TJLabsErrorCodeManager {
    val UNKNOWN = TJLabsErrorCode(
        domain = TJLabsErrorDomain.COMMON,
        code = 1000,
        name = "UNKNOWN",
        meaning = "알 수 없는 오류/예외"
    )

    private val codes: List<TJLabsErrorCode> = listOf(
        UNKNOWN,
        TJLabsErrorCode(TJLabsErrorDomain.AUTH, 1100, "AUTH_FAILED", "인증/토큰 발급·검증 전체 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.AUTH, 1101, "CREDENTIALS_MISSING", "accessKey/secret/clientSecret 누락"),
        TJLabsErrorCode(TJLabsErrorDomain.AUTH, 1102, "TOKEN_REFRESH_FAILED", "토큰 갱신 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.AUTH, 1103, "NOT_AUTHORIZED", "인증되지 않은 상태"),
        TJLabsErrorCode(TJLabsErrorDomain.AUTH, 1104, "LOGIN_FAILED", "로그인 API 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.INIT, 1200, "INVALID_ID", "유효하지 않은 ID"),
        TJLabsErrorCode(TJLabsErrorDomain.INIT, 1201, "INVALID_MODE", "유효하지 않은 모드"),
        TJLabsErrorCode(TJLabsErrorDomain.INIT, 1202, "RESOURCE_LOAD_FAILED", "리소스/초기 데이터 로드 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.INIT, 1203, "CALC_INIT_FAIL", "Calc/Engine 초기화 단계 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.INIT, 1204, "INVALID_PARAMETER", "유효하지 않은 파라미터"),
        TJLabsErrorCode(TJLabsErrorDomain.SERVICE, 1300, "NOT_INITIALIZED", "초기화 없이 시작 요청"),
        TJLabsErrorCode(TJLabsErrorDomain.SERVICE, 1301, "DUPLICATED_SERVICE", "이미 시작된 서비스 재시작"),
        TJLabsErrorCode(TJLabsErrorDomain.SERVICE, 1302, "SERVICE_STOPPED", "서비스 중지 상태"),
        TJLabsErrorCode(TJLabsErrorDomain.SERVICE, 1303, "SERVICE_ALREADY_STOPPED", "이미 중지된 서비스에 중지 요청"),
        TJLabsErrorCode(TJLabsErrorDomain.NETWORK, 1400, "NETWORK_DISCONNECT", "네트워크 단절"),
        TJLabsErrorCode(TJLabsErrorDomain.NETWORK, 1401, "HTTP_4XX", "요청 파라미터/권한 등 클라이언트 오류"),
        TJLabsErrorCode(TJLabsErrorDomain.NETWORK, 1402, "HTTP_5XX", "서버 내부 오류"),
        TJLabsErrorCode(TJLabsErrorDomain.NETWORK, 1403, "NETWORK_TIMEOUT", "네트워크 타임아웃"),
        TJLabsErrorCode(TJLabsErrorDomain.NETWORK, 1404, "HTTP_401_UNAUTHORIZED", "인증 실패(401)"),
        TJLabsErrorCode(TJLabsErrorDomain.NETWORK, 1405, "HTTP_403_FORBIDDEN", "권한 없음(403)"),
        TJLabsErrorCode(TJLabsErrorDomain.NETWORK, 1406, "HTTP_404_NOT_FOUND", "리소스 없음(404)"),
        TJLabsErrorCode(TJLabsErrorDomain.GENERATOR, 1500, "GENERATOR_FAIL", "위치/엔진 런타임 동작 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.GENERATOR, 1501, "GENERATOR_PRECHECK_FAIL", "엔진 사전점검 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.GENERATOR, 1502, "SIMULATION_DATA_LOAD_FAIL", "시뮬레이션 데이터 로드 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.GENERATOR, 1503, "SIMULATION_INVALID_FORMAT", "시뮬레이션 데이터 형식 오류"),
        TJLabsErrorCode(TJLabsErrorDomain.PERMISSION_BLE, 1600, "PERMISSION_DENIED", "권한 거부"),
        TJLabsErrorCode(TJLabsErrorDomain.PERMISSION_BLE, 1601, "BLUETOOTH_OFF", "블루투스 비활성화"),
        TJLabsErrorCode(TJLabsErrorDomain.PERMISSION_BLE, 1602, "BLUETOOTH_UNAVAILABLE", "BLE 미지원/사용 불가"),
        TJLabsErrorCode(TJLabsErrorDomain.PERMISSION_BLE, 1603, "BLE_SCAN_STOP", "BLE 스캔 중단/타임아웃"),
        TJLabsErrorCode(TJLabsErrorDomain.PERMISSION_BLE, 1604, "DUPLICATE_SCAN_START", "BLE 스캔 중복 시작"),
        TJLabsErrorCode(TJLabsErrorDomain.VM, 1700, "WEBVIEW_INIT_FAIL", "WebView/Bridge 초기화 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.VM, 1701, "VM_VIEW_FAIL", "VM View 초기화 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.NAVI, 1800, "ROUTE_REQUEST_FAILED", "경로 요청 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.NAVI, 1801, "ROUTE_GUIDANCE_OUT", "경로 이탈"),
        TJLabsErrorCode(TJLabsErrorDomain.NAVI, 1802, "ROUTE_NOT_FOUND", "경로 없음"),
        TJLabsErrorCode(TJLabsErrorDomain.NAVI, 1803, "NAVIGATION_ROUTE_FAILED", "내부 경로 생성 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1900, "RESOURCE_DOMAIN_ERROR", "리소스 도메인 일반 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1901, "RESOURCE_SECTOR_ERROR", "Sector 데이터 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1902, "RESOURCE_PATH_PIXEL_ERROR", "PathPixel 데이터 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1903, "RESOURCE_IMAGE_ERROR", "이미지 로드 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1904, "RESOURCE_AFFINE_ERROR", "Affine 데이터 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1905, "RESOURCE_NODE_LINK_ERROR", "Node/Link 통합 실패(legacy aggregate)"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1906, "RESOURCE_SCALE_ERROR", "Scale 데이터 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1907, "RESOURCE_ENTRANCE_ERROR", "Entrance 데이터 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1908, "RESOURCE_LEVEL_UNITS_ERROR", "LevelUnits 데이터 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1909, "RESOURCE_PARAM_ERROR", "파라미터 데이터 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1910, "RESOURCE_GEOFENCE_ERROR", "Geofence 데이터 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1911, "RESOURCE_BUILDING_LEVEL_ERROR", "Building/Level 데이터 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1912, "RESOURCE_LEVEL_WARDS_ERROR", "LevelWards 데이터 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1913, "RESOURCE_NODE_ERROR", "Node 데이터 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1914, "RESOURCE_LINK_ERROR", "Link 데이터 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1915, "RESOURCE_LANDMARK_ERROR", "Landmark 데이터 실패"),
        TJLabsErrorCode(TJLabsErrorDomain.RESOURCE, 1916, "RESOURCE_SPOTS_ERROR", "Spots 데이터 실패")
    )

    private val codeMap: Map<Int, TJLabsErrorCode> = codes.associateBy { it.code }
    private val nameMap: Map<String, TJLabsErrorCode> = codes.associateBy { it.name }
    private val deprecatedNameAliasMap: Map<String, String> = mapOf(
        "RESOURCE_NODE_LINK_ERROR" to "RESOURCE_NODE_ERROR"
    )
    private val legacyAggregateCodeToSpecificCodes: Map<Int, List<Int>> = mapOf(
        1905 to listOf(1913, 1914)
    )

    fun getAll(): List<TJLabsErrorCode> = codes

    fun getByDomain(domain: TJLabsErrorDomain): List<TJLabsErrorCode> =
        codes.filter { it.domain == domain }

    fun fromCode(code: Int): TJLabsErrorCode = codeMap[code] ?: UNKNOWN

    fun fromName(name: String): TJLabsErrorCode = nameMap[name] ?: UNKNOWN

    fun isKnownCode(code: Int): Boolean = codeMap.containsKey(code)

    fun normalizeName(name: String): String = deprecatedNameAliasMap[name] ?: name

    fun fromNameNormalized(name: String): TJLabsErrorCode = fromName(normalizeName(name))

    fun getRecommendedSpecificCodes(legacyCode: Int): List<TJLabsErrorCode> {
        val specificCodes = legacyAggregateCodeToSpecificCodes[legacyCode] ?: return emptyList()
        return specificCodes.mapNotNull { codeMap[it] }
    }

    fun format(code: Int): String {
        val error = fromCode(code)
        return "[${error.domain.displayName}] ${error.code} ${error.name} - ${error.meaning}"
    }
}
