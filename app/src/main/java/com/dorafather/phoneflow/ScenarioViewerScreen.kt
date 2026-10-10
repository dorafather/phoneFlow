package com.dorafather.phoneflow

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.dorafather.phoneflow.net.ScenarioUpdateStore
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
    var checking by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showRestartConfirm by remember { mutableStateOf(false) }

    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text("업데이트 적용") },
            text = { Text("새 시나리오를 받았습니다. 적용하려면 앱을 다시 시작해야 합니다. 지금 재시작할까요?") },
            confirmButton = {
                TextButton(onClick = { restartApp(context) }) { Text("지금 재시작") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) { Text("나중에") }
            }
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        item {
            Text(
                "한글 시나리오 내용 - 총 ${lines.size}줄 (읽기 전용)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { lines = readScenarioLines(filesDir) }) {
                    Text("새로고침")
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REST_SCE_GITHUB_URL)))
                }) {
                    Text("Git원문")
                }
                Button(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        statusMessage = "GitHub에서 확인 중..."
                        ScenarioUpdateStore.checkAndApply(filesDir) { result ->
                            checking = false
                            when {
                                !result.ok -> statusMessage = "업데이트 실패: ${result.error}"
                                result.restartNeeded -> {
                                    statusMessage = "새 버전을 받았습니다 - 재시작하면 적용됩니다."
                                    showRestartConfirm = true
                                }
                                else -> {
                                    lines = readScenarioLines(filesDir)
                                    statusMessage = "이미 최신 버전입니다."
                                }
                            }
                        }
                    }
                ) {
                    Text(if (checking) "확인 중..." else "업데이트")
                }
            }
            statusMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
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

/**
 * 엔진이 rest.sce 핫리로드를 지원하지 않아(flowjni.cpp nativeInit이 재호출을
 * 무시함), 업데이트를 반영하려면 프로세스 자체를 새로 띄워야 한다. 데이터는
 * 전혀 지우지 않는 "프로세스만 재시작" - AlarmManager로 아주 짧은 지연 뒤
 * 런처 액티비티를 다시 켜도록 예약해두고, 현재 프로세스는 바로 종료한다
 * (Activity.recreate()는 네이티브 엔진 쪽 전역 상태(flowjni.cpp의 s_pApp)를
 * 초기화해주지 않아 쓸 수 없음 - 프로세스를 통째로 내려야 nativeInit이
 * 다시 불린다).
 */
private fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pendingIntent = PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 300, pendingIntent)
    Runtime.getRuntime().exit(0)
}

private fun readScenarioLines(filesDir: File): List<Pair<Int, String>> {
    val f = File(filesDir, "rest.sce")
    if (!f.exists()) return listOf(1 to "(한글 시나리오 내용을 찾을 수 없습니다)")
    return f.readLines(Charsets.UTF_8).mapIndexed { idx, line -> (idx + 1) to line }
}
