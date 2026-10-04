package com.dorafather.phoneflow

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.io.File

// GitHub의 실제 rest.sce 원문(main 브랜치, 늘 최신) - "한글 DSL 홍보" 목적과
// 향후 GitHub 기반 자동 갱신 방향(2026-10-04 dorafather 요청) 양쪽에 걸쳐
// 있어, 폰 안의 사본만 보여주지 않고 진짜 출처로 바로 넘어갈 수 있게 한다.
private const val REST_SCE_GITHUB_URL =
    "https://github.com/dorafather/phoneFlow/blob/main/app/src/main/assets/rest.sce"

/**
 * rest.sce 읽기 전용 뷰어 - "DSL 엔진을 통한 Flow 생태계의 한 축"이라는 관점에서
 * 사용자가 직접 자기 폰의 시나리오 전문을 들여다볼 수 있게 한다(2026-10-04
 * dorafather 요청 - 수정 기능은 아님, 한글 DSL 자체를 보여주는 홍보/투명성
 * 목적도 겸함). addr.ini는 서비스키를 담고 있어 이 화면에서 절대 다루지 않는다
 * - 그 값은 설정(⚙) 화면의 마스킹된 표시로만 접근 가능하다.
 */
@Composable
fun ScenarioViewerScreen(filesDir: File) {
    val context = LocalContext.current
    var lines by remember {
        mutableStateOf(readScenarioLines(filesDir))
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "한글 시나리오 내용 - 총 ${lines.size}줄 (읽기 전용)",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { lines = readScenarioLines(filesDir) }) {
                    Text("새로고침")
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REST_SCE_GITHUB_URL)))
                }) {
                    Text("GitHub에서 원문 보기")
                }
            }
        }
        items(lines) { (number, content) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = number.toString(),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = content,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun readScenarioLines(filesDir: File): List<Pair<Int, String>> {
    val f = File(filesDir, "rest.sce")
    if (!f.exists()) return listOf(1 to "(한글 시나리오 내용을 찾을 수 없습니다)")
    return f.readLines(Charsets.UTF_8).mapIndexed { idx, line -> (idx + 1) to line }
}
