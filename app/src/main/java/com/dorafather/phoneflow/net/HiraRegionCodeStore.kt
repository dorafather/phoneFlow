package com.dorafather.phoneflow.net

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * 건강보험심사평가원 병원정보서비스(getHospBasisList)의 sidoCd/sgguCd 검색 -
 * 2026-10-10 실기기 테스트 중 "법정동코드(lawd_codes.db)와 같은 체계"라는
 * 가정이 틀렸다는 걸 발견했다(강남구로 sidoCd=11/sgguCd=11680 조회하면
 * totalCount=0). opendata.hira.or.kr > 서비스 소개 > 용어설명 > 코드조회 >
 * 지역코드(TBOMA270)에서 전체 6페이지(298행)를 직접 확인한 결과, 심평원은
 * 법정동코드와 무관한 자체 6자리 코드(시도 2자리 + 그 안의 구/시/군
 * 일련번호 4자리, 예: 강남구=110001)를 쓴다 - 그래서 lawd_codes.db를
 * 재사용하지 못하고 이 전용 DB(hira_region_codes.db)를 새로 만들었다.
 *
 * 이 코드표는 양주군(310008)/양주시(312700)처럼 행정구역 개편 이전 구코드도
 * 그대로 남아있다(심평원이 과거 청구자료 호환을 위해 보존) - 검색 결과에
 * 둘 다 뜰 수 있지만, 사용자가 고르면 그 시점 코드로 바로 조회되므로 걸러낼
 * 필요는 없다고 보고 그대로 뒀다.
 */
object HiraRegionCodeStore {

    data class HiraRegionMatch(
        val name: String,  // 예: "강남구"
        val code: String   // 예: "110001" (앞 2자리=sidoCd, 전체 6자리=sgguCd)
    )

    // 코드 앞 2자리(sidoCd) -> 공식 시도명. EGEN(국립중앙의료원 응급의료정보)이
    // Q0/STAGE1에 시도 "이름"을 그대로 요구해서(코드가 아님) 2026-10-11에
    // 추가 - hira_region_codes.db 실제 행(예: 210001=부산남구, 220001=
    // 인천미추홀구)을 직접 읽어 접두사별 시도를 확인하고 매핑했다(세종만
    // 유일하게 41로 예외 - 2012년 신설이라 기존 11~39 뒤에 덧붙은 것으로
    // 추정, 추측이 아니라 세종시=410000 단독 행으로 실측 확인).
    private val SIDO_PREFIX_TO_NAME = mapOf(
        "11" to "서울특별시",
        "21" to "부산광역시",
        "22" to "인천광역시",
        "23" to "대구광역시",
        "24" to "광주광역시",
        "25" to "대전광역시",
        "26" to "울산광역시",
        "31" to "경기도",
        "32" to "강원특별자치도",
        "33" to "충청북도",
        "34" to "충청남도",
        "35" to "전북특별자치도",
        "36" to "전라남도",
        "37" to "경상북도",
        "38" to "경상남도",
        "39" to "제주특별자치도",
        "41" to "세종특별자치시"
    )

    fun sidoNameForCode(code: String): String? = SIDO_PREFIX_TO_NAME[code.take(2)]

    private fun dbFile(filesDir: File) = File(filesDir, "hira_region_codes.db")

    fun search(filesDir: File, query: String, limit: Int = 30): List<HiraRegionMatch> {
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
                "SELECT code, name FROM hira_region WHERE name LIKE ? ORDER BY (name = ?) DESC, length(name), name LIMIT ?",
                arrayOf("$q%", q, limit.toString())
            )
            cursor.use { c ->
                val out = mutableListOf<HiraRegionMatch>()
                while (c.moveToNext()) {
                    out.add(HiraRegionMatch(code = c.getString(0), name = c.getString(1)))
                }
                out
            }
        }
    }
}
