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
