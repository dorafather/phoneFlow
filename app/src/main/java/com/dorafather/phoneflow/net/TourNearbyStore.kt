package com.dorafather.phoneflow.net

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * "지도" 체크박스 브라우저(2026-10-25 추가)의 행사 카테고리 전용 -
 * ParkingNearbyStore와 같은 이유로 rest.sce를 거치지 않는다. 기존 "행사 >
 * 내 주변"(rest.sce의 TOUR주변* 체인)은 텍스트 채팅 명령 -> DSL -> 텍스트
 * 응답 경로라 체크박스를 켜는 즉시 조용히 조회하는 이 UI 패턴에는 안 맞는다
 * (매번 채팅창에 명령 메시지가 찍히는 부작용도 생김). 그래서 TourAPI
 * locationBasedList2를 Kotlin에서 직접 한 번 더 호출한다 - 기존 채팅 명령
 * 경로(rest.sce)는 그대로 둔 채 완전히 별개의 경로다.
 */
object TourNearbyStore {

    data class NearbyFestival(
        val name: String,
        val addr: String,
        val distanceMeters: Int,
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
        radius: Int = 5000
    ): List<NearbyFestival> {
        val url = "$domain/locationBasedList2" +
            "?serviceKey=${URLEncoder.encode(serviceKey, "UTF-8")}" +
            "&MobileOS=ETC&MobileApp=phoneFlow&_type=json&arrange=E" +
            "&numOfRows=$limit&pageNo=1&contentTypeId=15" +
            "&mapX=$userLon&mapY=$userLat&radius=$radius"
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
        // rest.sce의 TOUR주변 체인과 동일하게 totalCount>=1이면 items.item은
        // 항상 배열(TourAPI는 totalCount==1이어도 배열로 돌려줌 - 이 프로젝트의
        // 다른 공공데이터 API와 정반대 관례, project-phoneflow-tour-parking-bugfixes
        // 참고).
        val items = json.optJSONObject("response")?.optJSONObject("body")
            ?.optJSONObject("items")?.optJSONArray("item") ?: return emptyList()
        val results = mutableListOf<NearbyFestival>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val lat = item.optString("mapy").toDoubleOrNull() ?: continue
            val lon = item.optString("mapx").toDoubleOrNull() ?: continue
            results.add(
                NearbyFestival(
                    name = item.optString("title").ifBlank { "(이름 없음)" },
                    addr = item.optString("addr1"),
                    distanceMeters = item.optString("dist").toDoubleOrNull()?.toInt() ?: 0,
                    lat = lat,
                    lon = lon
                )
            )
        }
        return results
    }
}
