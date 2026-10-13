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

    /** "주유소"/"메뉴"/"브랜드" 공통 검색 피커 전용(2026-10-13 추가 - "휴게소명
     *  찾기가 너무 어렵다, 자동완성 있으면 좋겠다"는 요청, 같은 날 "목록은 넓게
     *  찾고 선택하면 그 한 곳만 나와야" 피드백으로 재설계) - 목록에서 고르는
     *  단계는 unit_name(원본 전체 이름, "치악(춘천)휴게소")을 부분일치로 넓게
     *  찾아 "치악(춘천)"처럼 방향 표시가 살아있는 형태로 보여준다("춘천"으로
     *  검색해도 핵심 이름이 다른 "치악(춘천)" 같은 곳까지 다 후보로 뜨게 하기
     *  위함 - 사람이 직접 보고 고를 땐 이렇게 넓게 찾는 게 맞다). 대신 이렇게
     *  "방향까지 포함한" 이름을 그대로 질의어로 보내면, rest.sce의 === 접두사
     *  필터가 그 한 곳만 정확히 걸러준다(핵심 이름만 질의하면 "춘천"처럼 여러
     *  곳의 괄호 속 방향 표시와 겹쳐 엉뚱한 곳까지 섞여 나오는 문제가 있었음).
     */
    fun search(filesDir: File, query: String, limit: Int = 30): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val f = dbFile(filesDir)
        if (!f.exists()) return emptyList()
        val db = try {
            SQLiteDatabase.openDatabase(f.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (_: Exception) {
            return emptyList()
        }
        return db.use {
            val cursor = it.rawQuery(
                "SELECT DISTINCT unit_name FROM ex_rest_area WHERE unit_name LIKE ? ORDER BY length(unit_name), unit_name LIMIT ?",
                arrayOf("%$q%", limit.toString())
            )
            cursor.use { c ->
                val out = mutableListOf<String>()
                while (c.moveToNext()) out.add(c.getString(0).removeSuffix("휴게소"))
                out
            }
        }
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
