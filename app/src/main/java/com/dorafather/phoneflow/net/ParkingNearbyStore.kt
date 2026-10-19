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
 * "주차장 내 주변"/"주차장 가격순" 전용 - 2026-10-19 추가. 다른 모든 "내
 * 주변" 기능(주유소/휴게소는 로컬 DB, 행사는 TourAPI의 locationBasedList2
 * 서버 측 반경검색)과 달리, 이 API(PrkSttusInfo_v1/PrkOprInfo_v1)는
 * sidoCd/sigunguCd 지역 단위 필터만 지원하고 반경(위도/경도+radius)이나
 * "이 시설 하나만" 조회할 파라미터가 전혀 없다. 그런데 시설정보에는
 * 위경도(prk_plce_entrc_la/lo)가, 운영정보에는 기본요금(basic_info.
 * parking_chrge_bs_chrge)이 들어있어 "데이터는 있는데 API가 정렬을 안
 * 해주는" 상황이다. 이 DSL 엔진(rest.sce)에는 정렬/제곱근 함수가 전혀 없어
 * (2부 2.5절 함수 카탈로그 참고) 거리/가격 비교 자체를 rest.sce 안에서 할
 * 수 없으므로, 이 두 기능만 예외적으로 rest.sce를 거치지 않고 Kotlin이
 * 직접 지역 목록을 받아와 계산·정렬한다(다른 "내 주변"은 전부 rest.sce가
 * 실제 외부 호출을 수행함 - 이 파일이 유일한 예외).
 *
 * 가격순 정렬은 시설정보(PrkSttusInfo_v1, 이름/주소)와 운영정보
 * (PrkOprInfo_v1, 요금)를 prk_center_id로 직접 조인한다 - 실측 결과
 * 같은 지역을 같은 페이지로 조회하면 두 오퍼레이션이 완전히 같은 순서로
 * ID를 돌려주지만(2026-10-19 확인), 그 우연한 순서에 기대지 않고 항상
 * 맵으로 명시적으로 조인한다.
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

    data class CheapestParking(
        val name: String,
        val addr: String,
        val spaceCount: String,
        val feeName: String,
        val agentName: String,
        val baseChargeWon: Int
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

    /** 동기 호출 - 반드시 메인 스레드 밖(Dispatchers.IO 등)에서 호출할 것. */
    private fun fetchJson(domain: String, operation: String, serviceKey: String, sidoCd: String, sigunguCd: String, fetchRows: Int): JSONObject {
        val url = "$domain/$operation" +
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
        return JSONObject(body)
    }

    /**
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
        val json = fetchJson(domain, "PrkSttusInfo_v1", serviceKey, sidoCd, sigunguCd, fetchRows)
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

    /**
     * sidoCd/sigunguCd 지역의 시설정보(이름/주소)와 운영정보(기본요금)를
     * prk_center_id로 조인해, 기본요금이 낮은 순으로 limit개를 돌려준다.
     * 무료 주차장은 기본요금이 "0"으로 내려와(실측 확인) 자연히 맨 앞에
     * 온다. 기본요금 필드를 숫자로 해석할 수 없는(응답에 아예 없거나 빈
     * 값인) 항목은 비교 불가능하므로 순위 계산에서 제외한다.
     */
    fun findCheapest(
        domain: String,
        serviceKey: String,
        sidoCd: String,
        sigunguCd: String,
        limit: Int = 5,
        fetchRows: Int = 100
    ): List<CheapestParking> {
        val facilityJson = fetchJson(domain, "PrkSttusInfo_v1", serviceKey, sidoCd, sigunguCd, fetchRows)
        val facilities = facilityJson.optJSONArray("PrkSttusInfo") ?: return emptyList()

        val priceByCenterId = mutableMapOf<String, Int>()
        val oprJson = fetchJson(domain, "PrkOprInfo_v1", serviceKey, sidoCd, sigunguCd, fetchRows)
        val oprItems = oprJson.optJSONArray("PrkOprInfo")
        if (oprItems != null) {
            for (i in 0 until oprItems.length()) {
                val item = oprItems.getJSONObject(i)
                val id = item.optString("prk_center_id")
                if (id.isBlank()) continue
                val price = item.optJSONObject("basic_info")
                    ?.optString("parking_chrge_bs_chrge")
                    ?.toIntOrNull() ?: continue
                // 같은 prk_center_id가 여러 번 나오면(실측으로 확인된 중복 레코드)
                // 더 낮은 요금을 우선한다 - "찾을 수 있는 가장 싼 조건"이 목적이므로.
                val existing = priceByCenterId[id]
                if (existing == null || price < existing) priceByCenterId[id] = price
            }
        }

        val candidates = mutableListOf<CheapestParking>()
        for (i in 0 until facilities.length()) {
            val item = facilities.getJSONObject(i)
            val id = item.optString("prk_center_id")
            val price = priceByCenterId[id] ?: continue
            candidates.add(
                CheapestParking(
                    name = item.optString("prk_plce_nm").ifBlank { "(이름 없음)" },
                    addr = item.optString("prk_plce_adres"),
                    spaceCount = item.optString("prk_cmprt_co"),
                    feeName = item.optString("parkng_chrge_se_nm"),
                    agentName = item.optString("prk_manage_agent_nm"),
                    baseChargeWon = price
                )
            )
        }
        return candidates.sortedBy { it.baseChargeWon }.take(limit)
    }
}
