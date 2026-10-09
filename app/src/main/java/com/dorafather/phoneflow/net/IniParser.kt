package com.dorafather.phoneflow.net

/**
 * "[섹션]"/"key=value" 줄 단위 포맷 - IniFileReader(libUtil)가 읽는 것과
 * 같은 포맷. WatchlistStore/ServiceCatalogStore가 공유하는 유일한 파서
 * (2026-10-09 전엔 WatchlistStore 안에 똑같은 함수가 중복돼 있었다).
 *
 * 섹션 등장 순서를 그대로 보존한다(LinkedHashMap) - Settings 화면의
 * "서비스별 활용신청" 표시 순서가 이 순서를 그대로 따른다.
 */
object IniParser {
    fun parse(text: String): Map<String, Map<String, String>> {
        val result = linkedMapOf<String, MutableMap<String, String>>()
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
