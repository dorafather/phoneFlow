package com.dorafather.phoneflow.net

import java.io.File
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale

/**
 * addr.ini 섹션에 label=/apply_url=(선택: health_check_path=) 키를
 * 선언하면 Settings 화면의 "서비스별 활용신청"/"키 상태 점검" 목록에
 * 자동으로 뜨게 하는 레지스트리 - 2026-10-09 "새 공공데이터 서비스를
 * 깃허브에서 addr.ini만 갱신해도 설정 화면에 자동으로 추가되게 하자"
 * 설계 결과(이전엔 SettingsScreen.kt에 7개 서비스가 전부 하드코딩돼
 * 있어서 서비스 하나 늘 때마다 앱 코드를 고쳐야 했다).
 *
 * 단, 지역 검색 피커/GPS 권한처럼 전용 UI가 필요한 기능 자체는 여전히
 * 앱 업데이트가 필요하다 - 이건 "활용신청 바로가기" 디스커버리만
 * 데이터 기반으로 만드는 것이다(설계 논의에서 합의된 범위).
 *
 * health_check_path는 domain= 뒤에 그대로 이어붙일 경로+쿼리 문자열이고,
 * 아래 4개 예약 플레이스홀더만 치환한다(기존 6개 서비스 점검 로직을
 * 전부 분석해서 나온, 그 이상은 필요 없는 집합):
 *   {KEY}         서비스키(URL 인코딩됨)
 *   {YESTERDAY}   어제 날짜 YYYYMMDD
 *   {THIS_YEAR}   올해 YYYY
 *   {MOLIT_YM}    2개월 전 YYYYMM(국토부류 API가 최신월엔 자료가 비어있는 경우 대비)
 * health_check_path가 없는 섹션은 "키 상태 점검" 목록에서 조용히 빠진다
 * (➖ 로 표시 - 아직 이 기능을 지원하도록 추가되지 않은 서비스).
 */
object ServiceCatalogStore {

    data class ServiceEntry(
        val key: String,
        val label: String,
        val applyUrl: String,
        val healthCheckUrl: String?
    )

    private fun file(filesDir: File) = File(filesDir, "addr.ini")

    /** label=/apply_url= 둘 다 있는 섹션만, addr.ini에 등장하는 순서 그대로 돌려준다. */
    fun readAll(filesDir: File, serviceKey: String?): List<ServiceEntry> {
        val f = file(filesDir)
        if (!f.exists()) return emptyList()
        val sections = IniParser.parse(f.readText())
        return sections.mapNotNull { (sectionName, kv) ->
            val label = kv["label"] ?: return@mapNotNull null
            val applyUrl = kv["apply_url"] ?: return@mapNotNull null
            val domain = kv["domain"]
            val healthPath = kv["health_check_path"]
            val healthUrl = if (!domain.isNullOrBlank() && !healthPath.isNullOrBlank() && !serviceKey.isNullOrBlank()) {
                domain + resolvePlaceholders(healthPath, serviceKey)
            } else null
            ServiceEntry(sectionName, label, applyUrl, healthUrl)
        }
    }

    private fun resolvePlaceholders(template: String, serviceKey: String): String {
        val enc = URLEncoder.encode(serviceKey, "UTF-8")
        val cal = Calendar.getInstance()
        val thisYear = cal.get(Calendar.YEAR)
        cal.add(Calendar.DAY_OF_MONTH, -1)
        val yesterday = String.format(
            Locale.US, "%04d%02d%02d",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
        )
        val molitCal = Calendar.getInstance().apply { add(Calendar.MONTH, -2) }
        val molitYm = String.format(
            Locale.US, "%04d%02d",
            molitCal.get(Calendar.YEAR), molitCal.get(Calendar.MONTH) + 1
        )
        return template
            .replace("{KEY}", enc)
            .replace("{YESTERDAY}", yesterday)
            .replace("{THIS_YEAR}", thisYear.toString())
            .replace("{MOLIT_YM}", molitYm)
    }
}
