package com.dorafather.phoneflow.net

import java.io.File

/**
 * 설정 화면의 "등록 현황" 섹션 전용 - addr.ini의 4개 관심종목/관심지역
 * WATCHLIST 섹션(rest.sce가 함수.설정저장/SETINI로 직접 쓰는 대상)을
 * 그대로 읽기만 한다. 외부 API 호출 없이 즉시 표시하는 게 목적이라
 * ("주식 관심종목 조회" 등 채팅 명령은 등록된 항목마다 실시간 시세/날씨를
 * 다시 조회해 느림) 이 파일은 순수 파싱 전용이고, 엔진(rest.sce)이 쓰는
 * 섹션/슬롯 이름과 "없음" 빈 슬롯 규칙(addr.ini 시드 주석 참고)을 그대로
 * 미러링한다.
 */
object WatchlistStore {

    private const val EMPTY_SLOT = "없음"

    private data class Category(val label: String, val section: String, val slotPrefix: String)

    private val CATEGORIES = listOf(
        Category("주식 관심종목", "KRX_WATCHLIST", "종목"),
        Category("기상청 관심지역", "KMA_WATCHLIST", "지역"),
        Category("미세먼지 관심지역", "KECO_WATCHLIST", "지역"),
        Category("실거래가 관심지역", "MOLIT_WATCHLIST", "지역"),
    )

    data class CategoryStatus(val label: String, val items: List<String>)

    // 드로워의 "OO 지역 조회" 하위트리 전용 - 명령어 그룹 라벨("기상청"/"미세먼지"/
    // "실거래가")로 바로 찾을 수 있게 CATEGORIES와 별도로 키를 둔다. 주식은
    // rest.sce에 단건 조회 명령 자체가 없어 포함하지 않는다(MainActivity의
    // REGION_QUERY_TEMPLATES와 그룹 라벨 기준으로 1:1 대응).
    private val GROUP_LABEL_TO_CATEGORY = mapOf(
        "기상청" to Category("기상청 관심지역", "KMA_WATCHLIST", "지역"),
        "미세먼지" to Category("미세먼지 관심지역", "KECO_WATCHLIST", "지역"),
        "실거래가" to Category("실거래가 관심지역", "MOLIT_WATCHLIST", "지역"),
        "주식" to Category("주식 관심종목", "KRX_WATCHLIST", "종목"),
    )

    /**
     * "강남구:11680"/"삼성전자:005930"(콜론 구분, MOLIT/주식 등)과 "서울특별시
     * 종로구 청운효자동@61@126"(골뱅이 구분, 기상청 전국 격자 - 2026-10-09 추가)
     * 둘 다 코드가 붙은 값에서 조회 명령에 쓸 이름만 돌려준다.
     */
    fun itemNamesForGroup(filesDir: File, groupLabel: String): List<String> {
        val cat = GROUP_LABEL_TO_CATEGORY[groupLabel] ?: return emptyList()
        val f = File(filesDir, "addr.ini")
        if (!f.exists()) return emptyList()
        val section = parseIni(f.readText())[cat.section] ?: emptyMap()
        return (1..5).mapNotNull { i ->
            section["${cat.slotPrefix}$i"]?.trim()
                ?.takeIf { it.isNotEmpty() && it != EMPTY_SLOT }
                ?.substringBefore(":")
                ?.substringBefore("@")
        }
    }

    fun readAll(filesDir: File): List<CategoryStatus> {
        val f = File(filesDir, "addr.ini")
        val sections = if (f.exists()) parseIni(f.readText()) else emptyMap()
        return CATEGORIES.map { cat ->
            val section = sections[cat.section] ?: emptyMap()
            val items = (1..5).mapNotNull { i ->
                section["${cat.slotPrefix}$i"]?.trim()?.takeIf { it.isNotEmpty() && it != EMPTY_SLOT }
            }
            CategoryStatus(cat.label, items)
        }
    }

    /** "[섹션]"/"key=value" 줄 단위 포맷 - IniFileReader(libUtil)가 읽는 것과 같은 포맷. */
    private fun parseIni(text: String): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, MutableMap<String, String>>()
        var current: MutableMap<String, String>? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue
            if (line.startsWith("[") && line.endsWith("]")) {
                current = result.getOrPut(line.substring(1, line.length - 1)) { mutableMapOf() }
                continue
            }
            val idx = line.indexOf('=')
            if (idx <= 0) continue
            current?.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim())
        }
        return result
    }
}
