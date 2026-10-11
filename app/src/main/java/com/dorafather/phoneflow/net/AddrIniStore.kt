package com.dorafather.phoneflow.net

import java.io.File
import java.net.URLDecoder

private const val PLACEHOLDER = "<여기에 입력하세요>"

/**
 * addr.ini의 "공급자"(provider) 단위 인증키를 읽고/쓰는 헬퍼. 공급자는
 * service_key= 줄을 가진 섹션이면 전부 자동 인식된다(K_DATA/KOBIS/EX 등
 * 특정 이름을 하드코딩하지 않음) - provider_label=/provider_login_url=/
 * provider_signup_url=(전부 선택)로 설정 화면 표시를 꾸밀 수 있다. 새
 * 공급자(영화진흥위원회/한국도로공사 등 data.go.kr과 별개로 자체 인증키를
 * 발급하는 기관)를 추가할 때 addr.ini에 섹션 하나만 추가하면 설정 화면의
 * "공급자 목록"에 자동으로 나타난다(2026-10-12 설계 - K_DATA 하나만
 * 있다고 가정했던 기존 구조를 일반화).
 *
 * IniParser(libUtil과 동일한 "[섹션]/key=value" 포맷)로 읽되, 쓰기는 대상
 * 섹션의 service_key= 줄 하나만 정확히 치환한다(섹션 경계를 벗어난 동일
 * 이름의 줄까지 건드리지 않도록 섹션 범위로 치환 범위를 좁힘) - 그 외
 * 파일 내용(주석/포맷/다른 섹션)은 절대 건드리지 않는다.
 *
 * 엔진(libUtil의 IniFileReader)은 파일 변경을 1초 폴링으로 감지해 값
 * "갱신"은 즉시 반영한다(삭제만 반영 안 됨 - NotebookFlow CLAUDE.md
 * 1.16절과 동일한 제약, 이 경로는 삭제가 아니라 갱신이라 안전).
 */
object AddrIniStore {

    data class Provider(
        val section: String,
        val label: String,
        val loginUrl: String?,
        val signupUrl: String?
    )

    // data.go.kr이 보여주는 두 형태(Encoding/Decoding) 중 "Encoding"(이미
    // 퍼센트 인코딩된) 형태를 사용자가 붙여넣었는지 감지하는 휴리스틱 -
    // FlowMessageRouter.buildUrl()이 모든 파라미터 값을 URLEncoder로 정확히
    // 한 번 인코딩하므로, 이미 인코딩된 값을 그대로 저장하면 이중 인코딩되어
    // 인증이 깨진다.
    private val PERCENT_ENCODED = Regex("%[0-9A-Fa-f]{2}")

    private fun file(filesDir: File) = File(filesDir, "addr.ini")

    /** service_key= 줄이 있는 모든 섹션을 공급자로 인식해, addr.ini 등장 순서대로 돌려준다. */
    fun readProviders(filesDir: File): List<Provider> {
        val f = file(filesDir)
        if (!f.exists()) return emptyList()
        val sections = IniParser.parse(f.readText())
        return sections.mapNotNull { (name, kv) ->
            if (!kv.containsKey("service_key")) return@mapNotNull null
            Provider(
                section = name,
                label = kv["provider_label"] ?: name,
                loginUrl = kv["provider_login_url"],
                signupUrl = kv["provider_signup_url"]
            )
        }
    }

    fun readMasked(filesDir: File, section: String): String? {
        val key = readRaw(filesDir, section) ?: return null
        if (key.isBlank() || key == PLACEHOLDER) return null
        return mask(key)
    }

    fun readRaw(filesDir: File, section: String): String? {
        val f = file(filesDir)
        if (!f.exists()) return null
        return IniParser.parse(f.readText())[section]?.get("service_key")?.trim()
    }

    /** 저장 성공 시 true. 입력값이 비어있거나 대상 섹션에 service_key= 줄이 없으면 false. */
    fun save(filesDir: File, section: String, newKeyRaw: String): Boolean {
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

        // 대상 섹션의 본문 범위만 잘라내 그 안에서만 service_key= 줄을
        // 치환한다 - 다른 섹션에 우연히 같은 줄이 있어도 건드리지 않는다.
        val headerRegex = Regex("(?m)^\\[" + Regex.escape(section) + "\\]\\s*$")
        val headerMatch = headerRegex.find(content) ?: return false
        val bodyStart = headerMatch.range.last + 1
        val nextHeaderMatch = Regex("(?m)^\\[").find(content, bodyStart)
        val bodyEnd = nextHeaderMatch?.range?.first ?: content.length

        val before = content.substring(0, bodyStart)
        var body = content.substring(bodyStart, bodyEnd)
        val after = content.substring(bodyEnd)

        val keyLineRegex = Regex("(?m)^service_key=.*$")
        if (!keyLineRegex.containsMatchIn(body)) return false
        body = keyLineRegex.replaceFirst(body, "service_key=$normalized")

        f.writeText(before + body + after)
        return true
    }

    fun hasRealKey(filesDir: File, section: String): Boolean {
        val key = readRaw(filesDir, section)
        return !key.isNullOrBlank() && key != PLACEHOLDER
    }

    private fun mask(key: String): String {
        if (key.length <= 10) return "*".repeat(key.length)
        return key.take(6) + "..." + key.takeLast(4)
    }
}
