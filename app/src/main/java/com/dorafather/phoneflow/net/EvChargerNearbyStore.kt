package com.dorafather.phoneflow.net

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * "전기차충전소 내 주변"(2026-11-01 "지도 서비스에 추가" 요청) - ParkingNearbyStore와
 * 같은 이유로 rest.sce를 거치지 않고 Kotlin이 직접 getChargerInfo를 호출한다:
 * 이 API(한국환경공단 EvCharger)는 zcode/zscode 지역 단위 필터만 지원하고
 * 반경(위경도+radius) 검색이 전혀 없다(요청 메시지 명세 참고, EvChargerRegionCodeStore가
 * GPS를 zcode/zscode로 바꿔줌).
 *
 * 응답은 "충전소" 단위가 아니라 "충전기" 단위 레코드다 - 같은 statId(충전소ID)가
 * 충전기 대수만큼 여러 줄로 내려온다(statId+chgerId가 복합키). 지도 핀은
 * 충전소 하나당 하나여야 하므로 statId로 묶어, 각 충전소의 여러 충전기
 * 상태/타입을 한 핀의 상세내역으로 합친다. getChargerInfo 응답 자체에
 * stat/statUpdDt가 포함돼 있어(상태 전용 getChargerStatus를 별도로 부를
 * 필요 없음 - GoCamping처럼 N+1 없음.
 */
object EvChargerNearbyStore {

    data class ChargerUnit(
        val chgerId: String,
        val chgerTypeLabel: String,
        val stat: String,
        val statLabel: String,
        val output: String
    )

    data class NearbyStation(
        val statId: String,
        val name: String,
        val addr: String,
        val addrDetail: String,
        val location: String,
        val useTime: String,
        val bnm: String,
        val busiNm: String,
        val busiCall: String,
        val parkingFree: String,
        val note: String,
        val limitYn: String,
        val limitDetail: String,
        val lat: Double,
        val lon: Double,
        val distanceMeters: Int,
        val chargers: List<ChargerUnit>
    )

    // 3.3절 chgerType 공통코드.
    private val CHGER_TYPE_LABELS = mapOf(
        "01" to "DC차데모",
        "02" to "AC완속",
        "03" to "DC차데모+AC3상",
        "04" to "DC콤보",
        "05" to "DC차데모+DC콤보",
        "06" to "DC차데모+AC3상+DC콤보",
        "07" to "AC3상",
        "08" to "DC콤보(완속)",
        "09" to "NACS",
        "10" to "DC콤보+NACS",
        "11" to "DC콤보2(버스전용)"
    )

    // 3.2절 stat 공통코드(항목 설명 본문의 축약판과 달리 6/9까지 포함된
    // 완전한 쪽을 기준으로 삼음).
    private val STAT_LABELS = mapOf(
        "0" to "알수없음",
        "1" to "통신이상",
        "2" to "충전대기",
        "3" to "충전중",
        "4" to "운영중지",
        "5" to "점검중",
        "6" to "예약중",
        "9" to "상태미확인"
    )

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /** 동기 호출 - 반드시 메인 스레드 밖(Dispatchers.IO 등)에서 호출할 것.
     *  fetchRows가 충전소가 아니라 충전기 레코드 수 기준이라(한 충전소에
     *  여러 대) ParkingNearbyStore보다 넉넉히(300) 받아온다. */
    fun findNearest(
        domain: String,
        serviceKey: String,
        zcode: String,
        zscode: String,
        userLat: Double,
        userLon: Double,
        limit: Int = 5,
        fetchRows: Int = 300
    ): List<NearbyStation> {
        val url = "$domain/getChargerInfo" +
            "?serviceKey=${URLEncoder.encode(serviceKey, "UTF-8")}" +
            "&numOfRows=$fetchRows&pageNo=1&dataType=JSON" +
            "&zcode=$zcode&zscode=$zscode"
        var conn: HttpURLConnection? = null
        val body = try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        } finally {
            conn?.disconnect()
        }
        val json = JSONObject(body)
        // TourAPI 계열(response/header/body)과 달리 이 API는 포장 없이
        // items.item이 최상위에 바로 온다(2.1절 응답 메시지 예제 참고).
        val items = json.optJSONObject("items")?.optJSONArray("item") ?: return emptyList()

        // statId 순서를 보존하며 그룹핑(LinkedHashMap) - 같은 충전소의 여러
        // 충전기 줄을 한데 모은다.
        val grouped = LinkedHashMap<String, MutableList<JSONObject>>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val statId = item.optString("statId")
            if (statId.isBlank()) continue
            grouped.getOrPut(statId) { mutableListOf() }.add(item)
        }

        val candidates = mutableListOf<NearbyStation>()
        for ((statId, rows) in grouped) {
            val first = rows.first()
            val lat = first.optString("lat").toDoubleOrNull() ?: continue
            val lon = first.optString("lng").toDoubleOrNull() ?: continue
            val chargers = rows.map { row ->
                val typeCode = row.optString("chgerType")
                val stat = row.optString("stat")
                ChargerUnit(
                    chgerId = row.optString("chgerId"),
                    chgerTypeLabel = CHGER_TYPE_LABELS[typeCode] ?: typeCode.ifBlank { "알수없음" },
                    stat = stat,
                    statLabel = STAT_LABELS[stat] ?: "알수없음",
                    output = row.optString("output")
                )
            }
            candidates.add(
                NearbyStation(
                    statId = statId,
                    name = first.optString("statNm").ifBlank { "(이름 없음)" },
                    addr = first.optString("addr"),
                    addrDetail = first.optString("addrDetail"),
                    location = first.optString("location"),
                    useTime = first.optString("useTime"),
                    bnm = first.optString("bnm"),
                    busiNm = first.optString("busiNm"),
                    busiCall = first.optString("busiCall"),
                    parkingFree = first.optString("parkingFree"),
                    note = first.optString("note"),
                    limitYn = first.optString("limitYn"),
                    limitDetail = first.optString("limitDetail"),
                    lat = lat,
                    lon = lon,
                    distanceMeters = haversineMeters(userLat, userLon, lat, lon).toInt(),
                    chargers = chargers
                )
            )
        }
        return candidates.sortedBy { it.distanceMeters }.take(limit)
    }
}
