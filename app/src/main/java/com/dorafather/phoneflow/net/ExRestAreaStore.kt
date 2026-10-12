package com.dorafather.phoneflow.net

import android.database.sqlite.SQLiteDatabase
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 한국도로공사 고속도로 휴게소 위치정보(ex_rest_areas.db, 2026-10-12 추가) -
 * data.ex.co.kr의 "고속도로 휴게소 기준정보 현황" API(locationinfoRest)를
 * 발급받은 EX 키로 1회 호출해(전국 203개, 페이지네이션 3회) 직접 만든
 * 정적 DB다 - xValue/yValue가 TM 좌표계일까 우려했으나 실측 결과 그냥
 * WGS84 십진도(위경도)라 변환 없이 그대로 쓸 수 있었다.
 *
 * name 컬럼은 원본 unitName("서울만남(부산)휴게소")에서 괄호 앞부분만 뽑은
 * "핵심 이름"("서울만남")이다 - 주유소 가격 API(curStateStation)의
 * serviceAreaName 파라미터가 부분일치로 동작하고, 그 쪽 응답은 접미사가
 * "휴게소"가 아니라 "주유소"라서(예: "안성(부산)주유소") 접미사를 맞춰
 * 비교하는 대신 애초에 접미사 없는 핵심 이름으로 질의하는 쪽을 택했다
 * (실측으로 "죽전" 검색이 "죽전(서울)주유소"에 정확히 매치됨을 확인).
 */
object ExRestAreaStore {

    data class RestAreaLocation(val name: String, val lat: Double, val lon: Double)

    private fun dbFile(filesDir: File) = File(filesDir, "ex_rest_areas.db")

    private fun readAll(filesDir: File): List<RestAreaLocation> {
        val f = dbFile(filesDir)
        if (!f.exists()) return emptyList()
        val db = try {
            SQLiteDatabase.openDatabase(f.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (_: Exception) {
            return emptyList()
        }
        return db.use {
            val cursor = it.rawQuery("SELECT name, lat, lon FROM ex_rest_area", null)
            cursor.use { c ->
                val out = mutableListOf<RestAreaLocation>()
                while (c.moveToNext()) {
                    out.add(RestAreaLocation(c.getString(0), c.getDouble(1), c.getDouble(2)))
                }
                out
            }
        }
    }

    /** Haversine 공식으로 실제 지구 곡률을 반영한 거리(km)를 구해 가장 가까운 [limit]곳을 돌려준다.
     *  203행뿐이라 공간 인덱스 없이 매번 전체를 스캔해도 비용이 미미하다. */
    fun nearest(filesDir: File, lat: Double, lon: Double, limit: Int = 5): List<RestAreaLocation> {
        val all = readAll(filesDir)
        if (all.isEmpty()) return emptyList()
        return all.sortedBy { haversineKm(lat, lon, it.lat, it.lon) }.take(limit)
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
