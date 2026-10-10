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
