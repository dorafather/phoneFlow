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
 * "주차장 내 주변" 전용 - 2026-10-19 추가. 다른 모든 "내 주변" 기능(주유소/
 * 휴게소는 로컬 DB, 행사는 TourAPI의 locationBasedList2 서버 측 반경검색)과
 * 달리, 이 API(PrkSttusInfo_v1)는 sidoCd/sigunguCd 지역 단위 필터만 지원하고
 * 반경(위도/경도+radius) 파라미터 자체가 없다 - 그런데 각 주차장 항목에는
 * 위경도(prk_plce_entrc_la/lo)가 들어있어, 실제 거리순 정렬이 "데이터는
 * 있는데 API가 안 해주는" 상황이다. 이 DSL 엔진(rest.sce)에는 정렬/제곱근
 * 함수가 전혀 없어(2부 2.5절 함수 카탈로그 참고) 거리 계산 자체를 rest.sce
 * 안에서 할 수 없으므로, 이 기능만 예외적으로 rest.sce를 거치지 않고
 * Kotlin이 직접 지역 목록을 받아와 거리를 계산·정렬한다(다른 "내 주변"은
 * 전부 rest.sce가 실제 외부 호출을 수행함 - 이 파일이 유일한 예외).
 */
object ParkingNearbyStore {

    data class NearbyParking(
        val name: String,
        val addr: String,
        val spaceCount: String,
        val feeName: String,
        val agentName: String,
        val distanceMeters: Int
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

    /**
     * 동기 호출 - 반드시 메인 스레드 밖(Dispatchers.IO 등)에서 호출할 것.
     * sidoCd/sigunguCd 지역의 주차장을 넉넉히(fetchRows) 받아온 뒤, 위경도가
     * 있는 항목만 실제 거리를 계산해 가까운 순으로 limit개를 돌려준다.
     */
    fun findNearest(
        domain: String,
        serviceKey: String,
        sidoCd: String,
        sigunguCd: String,
        userLat: Double,
        userLon: Double,
        limit: Int = 5,
        fetchRows: Int = 100
    ): List<NearbyParking> {
        val url = "$domain/PrkSttusInfo_v1" +
            "?serviceKey=${URLEncoder.encode(serviceKey, "UTF-8")}" +
            "&numOfRows=$fetchRows&pageNo=1&format=2" +
            "&sidoCd=$sidoCd&sigunguCd=$sigunguCd"
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
        val items = json.optJSONArray("PrkSttusInfo") ?: return emptyList()
        val candidates = mutableListOf<NearbyParking>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val lat = item.optString("prk_plce_entrc_la").toDoubleOrNull() ?: continue
            val lon = item.optString("prk_plce_entrc_lo").toDoubleOrNull() ?: continue
            candidates.add(
                NearbyParking(
                    name = item.optString("prk_plce_nm").ifBlank { "(이름 없음)" },
                    addr = item.optString("prk_plce_adres"),
                    spaceCount = item.optString("prk_cmprt_co"),
                    feeName = item.optString("parkng_chrge_se_nm"),
                    agentName = item.optString("prk_manage_agent_nm"),
                    distanceMeters = haversineMeters(userLat, userLon, lat, lon).toInt()
                )
            )
        }
        return candidates.sortedBy { it.distanceMeters }.take(limit)
    }
}
