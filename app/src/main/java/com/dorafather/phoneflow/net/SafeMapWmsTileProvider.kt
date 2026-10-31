package com.dorafather.phoneflow.net

import com.google.android.gms.maps.model.UrlTileProvider
import java.net.MalformedURLException
import java.net.URL
import java.net.URLEncoder
import kotlin.math.pow

/**
 * "지도" 체크박스 브라우저의 "범죄주의구간" 레이어(2026-10-31 추가) 전용 -
 * 행정안전부 생활안전지도(safemap.go.kr)의 WMS(Web Map Service)를 구글맵
 * TileOverlay로 겹쳐 그린다. 행사/주유소/메뉴/주차장(점 핀)과 완전히 다른
 * 종류의 레이어 - 서버가 위경도 범위(bbox)에 해당하는 "이미지 한 장"을
 * 돌려주는 방식이라, 구글맵이 요청하는 타일(x,y,zoom)을 Web Mercator
 * (EPSG:3857) bbox로 직접 변환해 WMS URL을 만들어야 한다.
 *
 * 범죄유형 5종(전체/강도/절도/성폭력/폭력)은 레이어명(A2SM_CRMNLHSPOT_TOT)은
 * 같지만 엔드포인트(IF_0083~0087_WMS)가 다르고, safemap.go.kr의 [오픈API
 * Data] 페이지에서 유형별로 "사용신청"을 따로 완료해야 실제 타일이 나온다
 * (2026-10-31 실측 - 신청 안 한 유형은 인증 오류로 빈 타일만 돌아옴).
 */
object SafeMapWmsTileProvider {

    enum class CrimeType(val label: String, val endpoint: String) {
        TOTAL("전체", "IF_0087_WMS"),
        BURGLARY("강도", "IF_0086_WMS"),
        THEFT("절도", "IF_0084_WMS"),
        RAPE("성폭력", "IF_0085_WMS"),
        VIOLENCE("폭력", "IF_0083_WMS")
    }

    // Web Mercator 상수(OSM/구글맵 타일 표준) - 지구 반지름 6378137m 기준.
    private const val TILE_SIZE = 256
    private val ORIGIN_SHIFT = 2.0 * Math.PI * 6378137.0 / 2.0
    private val INITIAL_RESOLUTION = 2.0 * Math.PI * 6378137.0 / TILE_SIZE

    private fun tileToBBox(x: Int, y: Int, zoom: Int): DoubleArray {
        val resolution = INITIAL_RESOLUTION / 2.0.pow(zoom)
        val minX = x * TILE_SIZE * resolution - ORIGIN_SHIFT
        val maxX = (x + 1) * TILE_SIZE * resolution - ORIGIN_SHIFT
        val maxY = ORIGIN_SHIFT - y * TILE_SIZE * resolution
        val minY = ORIGIN_SHIFT - (y + 1) * TILE_SIZE * resolution
        return doubleArrayOf(minX, minY, maxX, maxY)
    }

    fun create(domain: String, serviceKey: String, crimeType: CrimeType): UrlTileProvider {
        return object : UrlTileProvider(TILE_SIZE, TILE_SIZE) {
            override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
                val bbox = tileToBBox(x, y, zoom)
                val urlStr = "$domain/${crimeType.endpoint}" +
                    "?serviceKey=${URLEncoder.encode(serviceKey, "UTF-8")}" +
                    "&srs=EPSG:3857" +
                    "&bbox=${bbox[0]},${bbox[1]},${bbox[2]},${bbox[3]}" +
                    "&format=${URLEncoder.encode("image/png", "UTF-8")}" +
                    "&width=$TILE_SIZE&height=$TILE_SIZE&transparent=TRUE"
                return try {
                    URL(urlStr)
                } catch (_: MalformedURLException) {
                    null
                }
            }
        }
    }
}
