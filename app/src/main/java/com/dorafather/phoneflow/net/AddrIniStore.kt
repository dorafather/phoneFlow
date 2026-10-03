package com.dorafather.phoneflow.net

import java.io.File
import java.net.URLDecoder

private const val PLACEHOLDER = "<여기에 입력하세요>"

/**
 * filesDir/addr.ini의 [K_DATA] service_key 한 줄만 안전하게 읽고/쓰는
 * 헬퍼. IniParser(libUtil)와 동일한 "[섹션]/key=value" 줄 단위 포맷을
 * 그대로 다루되, 이 파일은 그 한 줄 외에는 절대 건드리지 않는다(다른
 * 섹션/키를 보존 - NotebookFlow의 PUT /config/addr가 전체를 통째로
 * 재구성해버리는 위험(1.6절)을 피하려고 일부러 줄 단위 치환만 한다).
 *
 * 엔진(libUtil의 IniFileReader)은 파일 변경을 1초 폴링으로 감지해 값
 * "갱신"은 즉시 반영한다(삭제만 반영 안 됨 - NotebookFlow CLAUDE.md
 * 1.16절과 동일한 제약, 이 경로는 삭제가 아니라 갱신이라 안전).
 */
object AddrIniStore {

    private val SERVICE_KEY_LINE = Regex("(?m)^service_key=.*$")
    // data.go.kr이 보여주는 두 형태(Encoding/Decoding) 중 "Encoding"(이미
    // 퍼센트 인코딩된) 형태를 사용자가 붙여넣었는지 감지하는 휴리스틱 -
    // FlowMessageRouter.buildUrl()이 모든 파라미터 값을 URLEncoder로 정확히
    // 한 번 인코딩하므로, 이미 인코딩된 값을 그대로 저장하면 이중 인코딩되어
    // 인증이 깨진다(소스 분석으로 확정 - 실측 A/B는 data.go.kr 포털 로그인이
    // 필요해 이번 범위에서 수행 못 함, 산출물 문서에 명시).
    private val PERCENT_ENCODED = Regex("%[0-9A-Fa-f]{2}")

    private fun file(filesDir: File) = File(filesDir, "addr.ini")

    fun readMasked(filesDir: File): String? {
        val key = readRaw(filesDir) ?: return null
        if (key.isBlank() || key == PLACEHOLDER) return null
        return mask(key)
    }

    fun readRaw(filesDir: File): String? {
        val f = file(filesDir)
        if (!f.exists()) return null
        val m = SERVICE_KEY_LINE.find(f.readText())
        val line = m?.value ?: return null
        return line.substringAfter("service_key=", "").trim()
    }

    /** 저장 성공 시 true. 입력값이 비어있으면 아무것도 하지 않고 false. */
    fun save(filesDir: File, newKeyRaw: String): Boolean {
        val trimmed = newKeyRaw.trim()
        if (trimmed.isEmpty()) return false
        val normalized = if (PERCENT_ENCODED.containsMatchIn(trimmed)) {
            // 이미 퍼센트 인코딩된(Encoding) 형태로 보이면 한 번 디코딩해
            // 항상 "Decoding" 형태로 저장한다 - buildUrl()이 그 형태를
            // 기대한다.
            try { URLDecoder.decode(trimmed, "UTF-8") } catch (_: Exception) { trimmed }
        } else trimmed

        val f = file(filesDir)
        if (!f.exists()) return false
        val content = f.readText()
        if (!SERVICE_KEY_LINE.containsMatchIn(content)) return false
        f.writeText(SERVICE_KEY_LINE.replace(content, "service_key=$normalized"))
        return true
    }

    fun hasRealKey(filesDir: File): Boolean {
        val key = readRaw(filesDir)
        return !key.isNullOrBlank() && key != PLACEHOLDER
    }

    private fun mask(key: String): String {
        if (key.length <= 10) return "*".repeat(key.length)
        return key.take(6) + "..." + key.takeLast(4)
    }
}
