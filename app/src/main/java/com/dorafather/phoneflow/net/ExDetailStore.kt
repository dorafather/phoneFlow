package com.dorafather.phoneflow.net

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * "지도" 체크박스 브라우저(2026-10-25 "주유소 가격/메뉴 상세정보" 요청으로
 * 추가)의 주유소/메뉴 카테고리 핀 클릭 상세용. ExRestAreaStore(로컬 DB,
 * 위치만 앎)와 달리 이건 rest.sce의 EX가격/EX메뉴 체인과 똑같은 EX 공공
 * API를 Kotlin에서 직접 호출한다 - 체크박스 브라우저는 채팅 왕복 경로를
 * 안 쓰므로(TourNearbyStore/ParkingNearbyStore와 같은 이유) rest.sce를
 * 우회하는 별도 경로가 필요함.
 *
 * 실측 확인(2026-10-25) 두 가지:
 * 1) data.ex.co.kr은 User-Agent가 없는 요청을 WAF가 "Request Blocked"로
 *    막는다(HttpURLConnection 기본값은 UA를 사실상 안 보내는 값이라 그대로
 *    걸림) - apis.data.go.kr(ParkingNearbyStore/TourNearbyStore)은 이 문제가
 *    없어 그쪽엔 없는 설정이지만 이 호스트엔 필수.
 * 2) ExRestAreaStore(로컬 DB)의 지역명과 이 API의 serviceAreaName/stdRestNm은
 *    정확히 일치하지 않는다("하남드림휴게소" 검색 시 "하남드림주유소" 등으로
 *    따로 존재) - rest.sce의 기존 "내 주변" 체인(EX근처응답처리/
 *    EX메뉴근처응답처리)도 정확 일치 검사 없이 응답의 첫 번째 결과를 그대로
 *    쓰는 걸 보고 동일하게 맞췄다(정확 일치를 요구하면 거의 항상 빈 결과가
 *    나옴 - 처음에 이렇게 짰다가 실기기에서 전부 빈 값으로 나와 발견한 버그).
 */
object ExDetailStore {

    data class GasPrice(
        val routeName: String,
        val direction: String,
        val oilCompany: String,
        val gasolinePrice: String,
        val diselPrice: String
    )

    data class MenuItem(val foodName: String, val foodCost: String)

    private fun fetchJson(url: String): JSONObject {
        var conn: HttpURLConnection? = null
        val body = try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
            }
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        } finally {
            conn?.disconnect()
        }
        return JSONObject(body)
    }

    // curStateStation(가격)의 serviceAreaName은 "OO휴게소"가 아니라 "OO주유소"
    // 식으로 저장돼 있어("하남드림휴게소" 검색은 0건, "하남드림"만 보내면
    // "하남드림주유소"가 매칭됨 - 2026-10-25 실측 확인), ExRestAreaStore가
    // 주는 전체 이름 끝의 이런 접미사를 떼고 한 번 더 시도한다. 반대로
    // restBestfoodList(메뉴)는 "OO휴게소" 전체 이름 그대로 매칭되므로
    // fetchMenuItems엔 이 처리가 필요 없다.
    private val AREA_SUFFIXES = listOf("졸음쉼터", "휴게소", "쉼터")

    /** rest.sce의 EX근처 체인과 동일하게 첫 번째 결과를 그대로 쓴다(이름
     *  정확 일치를 요구하지 않음 - 클래스 주석의 2번 참고). */
    fun fetchGasPrice(domain: String, serviceKey: String, areaName: String): GasPrice? {
        fun query(name: String): GasPrice? {
            val url = "$domain/business/curStateStation" +
                "?key=${URLEncoder.encode(serviceKey, "UTF-8")}&type=json" +
                "&serviceAreaName=${URLEncoder.encode(name, "UTF-8")}&numOfRows=5"
            val list = fetchJson(url).optJSONArray("list") ?: return null
            if (list.length() == 0) return null
            val item = list.getJSONObject(0)
            return GasPrice(
                routeName = item.optString("routeName"),
                direction = item.optString("direction"),
                oilCompany = item.optString("oilCompany"),
                gasolinePrice = item.optString("gasolinePrice"),
                diselPrice = item.optString("diselPrice")
            )
        }
        query(areaName)?.let { return it }
        val suffix = AREA_SUFFIXES.firstOrNull { areaName.endsWith(it) } ?: return null
        return query(areaName.removeSuffix(suffix))
    }

    /** rest.sce의 "전체메뉴"(EX전체메뉴조회전송)와 동일 - bestfoodyn 필터를
     *  안 둬서 대표메뉴만이 아니라 등록된 전체 메뉴를 돌려준다(2026-10-25
     *  "대표메뉴 말고 일반 상세 목록" 요청으로 bestfoodyn=Y 제거, numOfRows도
     *  전체메뉴 기준인 50으로 맞춤). 이름 정확 일치를 요구하지 않는 이유는
     *  클래스 주석 2번 참고 - 다만 "근처" 체인은 첫 항목 하나만 쓰는 것과
     *  달리, 여기선 바텀시트에 여러 줄 보여줄 여유가 있어 최대 10개까지 모은다. */
    fun fetchMenuItems(domain: String, serviceKey: String, areaName: String): List<MenuItem> {
        val url = "$domain/restinfo/restBestfoodList" +
            "?key=${URLEncoder.encode(serviceKey, "UTF-8")}&type=json" +
            "&stdRestNm=${URLEncoder.encode(areaName, "UTF-8")}&numOfRows=50"
        val list = fetchJson(url).optJSONArray("list") ?: return emptyList()
        val results = mutableListOf<MenuItem>()
        for (i in 0 until minOf(list.length(), 10)) {
            val item = list.getJSONObject(i)
            results.add(MenuItem(foodName = item.optString("foodNm"), foodCost = item.optString("foodCost")))
        }
        return results
    }
}
