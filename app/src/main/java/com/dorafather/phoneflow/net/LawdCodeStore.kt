package com.dorafather.phoneflow.net

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * 법정동코드 검색 - 국토교통부_법정동코드_20260813.csv(2026-10-08 dorafather
 * 제공, "존재" 상태만, 시/도 단독 행은 제외)로 미리 빌드해 assets에 번들한
 * lawd_codes.db(SQLite, 읽기 전용)를 검색한다. MOLIT 실거래가 API의
 * LAWD_CD는 실제로는 시/군/구 단위(5자리)만 받으므로(동 단위 10자리가
 * 아님 - 실측 확인, 1.21절 참고), 동/리 단위로 검색해도 실제로 등록·조회에
 * 쓰이는 건 그 상위 시군구의 5자리 코드다.
 *
 * 예: "신남동" 검색 → "인천광역시 미추홀구 신남동"/"서울특별시 ... 신남동" 등
 * 여러 결과가 나올 수 있고(실제로 전국에 동명 동/리가 3천 건 이상 존재함,
 * 2026-10-08 CSV로 직접 확인), 각 결과의 parentCode5/parentDisplayName은
 * 그 동/리가 속한 시군구를 가리킨다 - 사용자가 그중 하나를 고르면 그
 * 시군구가 등록된다.
 */
object LawdCodeStore {

    data class LawdMatch(
        val fullName: String,      // 예: "인천광역시 미추홀구 신남동" (후보 표시용)
        val parentDisplayName: String, // 예: "미추홀구" (실제 등록될 이름)
        val parentCode5: String    // 예: "28177" (실제 등록될 LAWD_CD)
    )

    private fun dbFile(filesDir: File) = File(filesDir, "lawd_codes.db")

    /**
     * query로 시작하는 동/읍/면/리/구/시 이름을 가진 행을 찾는다(마지막 지명
     * 토큰 기준 접두어 일치 - 타이핑 중간에도 바로 후보가 뜨도록). 결과가
     * 많을 수 있어 limit으로 자른다.
     */
    fun search(filesDir: File, query: String, limit: Int = 30): List<LawdMatch> {
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
                "SELECT code, name FROM lawd WHERE last_name LIKE ? ORDER BY (last_name = ?) DESC, length(name), name LIMIT ?",
                arrayOf("$q%", q, limit.toString())
            )
            cursor.use { c ->
                val out = mutableListOf<LawdMatch>()
                while (c.moveToNext()) {
                    val code = c.getString(0)
                    val name = c.getString(1)
                    out.add(toMatch(code, name))
                }
                out
            }
        }
    }

    /** 10자리 법정동코드 끝 5자리가 00000이면 이 행 자체가 시군구(또는 그 이상) 단위다. */
    private fun toMatch(code: String, name: String): LawdMatch {
        val tokens = name.split(" ")
        val isRegionLevelItself = code.length == 10 && code.substring(5) == "00000"
        val parentDisplay = if (isRegionLevelItself) {
            tokens.last()
        } else {
            // 동/리 단위 - 바로 위 시군구(또는 세종시처럼 구가 없으면 그 상위 시)
            // 토큰이 가리키는 이름. 세종특별자치시 산하 읍면동처럼 구가 아예 없는
            // 경우에도 "두 번째 토큰"이 정확히 그 상위 행정구역이 된다.
            if (tokens.size >= 2) tokens[tokens.size - 2] else tokens.last()
        }
        return LawdMatch(
            fullName = name,
            parentDisplayName = parentDisplay,
            parentCode5 = code.take(5)
        )
    }
}
