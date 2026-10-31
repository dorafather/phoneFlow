package com.dorafather.phoneflow.net

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * "지도" 체크박스 브라우저(2026-11-01 추가)의 고캠핑 카테고리 전용 -
 * TourNearbyStore/ParkingNearbyStore와 같은 이유로 rest.sce를 거치지 않고
 * GoCamping locationBasedList를 Kotlin에서 직접 한 번 호출한다(기존 채팅
 * 명령 "주변고캠핑"의 rest.sce 경로는 그대로 둔 채 완전히 별개).
 *
 * 행사(TourNearbyStore)와 달리 locationBasedList 응답 자체에 주소/전화/
 * 업종/부대시설 등 상세 필드가 다 들어있어 별도 상세(detail) API 호출이
 * 필요 없다(N+1 없음).
 */
object GoCampingNearbyStore {

    data class NearbyCamp(
        val name: String,
        val addr: String,
        val induty: String,
        val tel: String,
        val homepage: String,
        val sbrsCl: String,
        val animalCmgCl: String,
        val lat: Double,
        val lon: Double
    )

    /** 동기 호출 - 반드시 메인 스레드 밖에서 호출할 것. */
    fun findNearest(
        domain: String,
        serviceKey: String,
        userLat: Double,
        userLon: Double,
        limit: Int = 5,
        radius: Int = 10000
    ): List<NearbyCamp> {
        val url = "$domain/locationBasedList" +
            "?serviceKey=${URLEncoder.encode(serviceKey, "UTF-8")}" +
            "&MobileOS=ETC&MobileApp=phoneFlow&_type=json" +
            "&numOfRows=$limit&pageNo=1&mapX=$userLon&mapY=$userLat&radius=$radius"
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
        if (json.optJSONObject("response")?.optJSONObject("body")?.optInt("totalCount", 0) == 0) {
            return emptyList()
        }
        // rest.sce의 GOCAMPING 체인과 동일하게 totalCount>=1이면 items.item은
        // 항상 배열(TourAPI 계열 공통 - project-phoneflow-tour-parking-bugfixes
        // 참고).
        val items = json.optJSONObject("response")?.optJSONObject("body")
            ?.optJSONObject("items")?.optJSONArray("item") ?: return emptyList()
        val results = mutableListOf<NearbyCamp>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val lat = item.optString("mapY").toDoubleOrNull() ?: continue
            val lon = item.optString("mapX").toDoubleOrNull() ?: continue
            results.add(
                NearbyCamp(
                    name = item.optString("facltNm").ifBlank { "(이름 없음)" },
                    addr = item.optString("addr1"),
                    induty = item.optString("induty"),
                    tel = item.optString("tel"),
                    homepage = item.optString("homepage"),
                    sbrsCl = item.optString("sbrsCl"),
                    animalCmgCl = item.optString("animalCmgCl"),
                    lat = lat,
                    lon = lon
                )
            )
        }
        return results
    }
}
