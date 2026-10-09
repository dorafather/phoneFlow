package com.dorafather.phoneflow.net

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * 기상청 단기예보 격자(nx,ny) 검색 - 기상청41_단기예보 조회서비스_오픈API활용가이드의
 * 격자_위경도 엑셀(2026-07 기준)로 미리 빌드해 assets에 번들한 kma_grid.db(SQLite,
 * 읽기 전용)를 검색한다. MOLIT과 달리 여기는 상위 단위로 뭉칠 필요 없이 검색된
 * 읍면동 자신의 nx,ny를 그대로 쓴다(법정동코드 체계와 동일한 10자리 행정구역코드를
 * 쓰지만, API가 원하는 건 좌표뿐이라 이름+nx+ny 세 값만 저장).
 *
 * 원본 엑셀에는 2026-07-01 전남광주 통합/인천 개편 등 최근 행정구역 개편으로 생긴
 * 데이터 품질 문제(열 밀림, 자리수 깨짐, "상전면" 같은 스텁 값 반복)가 있어 build 시
 * 안전하게 해석 가능한 행만 추렸다(3,755/3,838행 - 제외된 행은 전부 해석이 모호한
 * 소수 사례, 1.21절 참고 스크립트로 재현 가능).
 */
object KmaGridStore {

    data class KmaMatch(
        val fullName: String, // 예: "서울특별시 종로구 청운효자동"
        val nx: Int,
        val ny: Int
    )

    private fun dbFile(filesDir: File) = File(filesDir, "kma_grid.db")

    /** query로 시작하는 이름(마지막 지명 토큰 기준 접두어 일치)을 찾는다. */
    fun search(filesDir: File, query: String, limit: Int = 30): List<KmaMatch> {
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
                "SELECT name, nx, ny FROM kma WHERE last_name LIKE ? ORDER BY (last_name = ?) DESC, length(name), name LIMIT ?",
                arrayOf("$q%", q, limit.toString())
            )
            cursor.use { c ->
                val out = mutableListOf<KmaMatch>()
                while (c.moveToNext()) {
                    out.add(KmaMatch(c.getString(0), c.getInt(1), c.getInt(2)))
                }
                out
            }
        }
    }
}
