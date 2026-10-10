package com.dorafather.phoneflow

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dorafather.phoneflow.net.AddrIniStore
import com.dorafather.phoneflow.net.AndroidHttpClient
import com.dorafather.phoneflow.net.ServiceCatalogStore
import com.dorafather.phoneflow.net.WatchlistStore

private enum class CheckStatus { UNKNOWN, CHECKING, OK, FAIL }

@Composable
fun SettingsScreen(filesDir: java.io.File) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var maskedKey by remember { mutableStateOf(AddrIniStore.readMasked(filesDir)) }
    var newKeyInput by remember { mutableStateOf("") }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    // 채팅 화면에서 "관심종목/지역 추가·삭제"로 바뀐 내용을 설정 화면에
    // 들어올 때마다 새로 반영하기 위해 remember 하나로 들고, 새로고침
    // 버튼으로도 다시 읽을 수 있게 한다 - API 호출 없이 addr.ini만 읽으므로
    // 매번 다시 읽어도 비용이 거의 없다.
    var watchlist by remember { mutableStateOf(WatchlistStore.readAll(filesDir)) }
    // readMasked()가 null이 아니면 "<여기에 입력하세요>" 플레이스홀더가 아닌
    // 실제 키가 저장돼 있다는 뜻 - 그때만 readRaw()를 점검 URL 조립에 넘긴다
    // (플레이스홀더 문자열 그대로 URL에 끼워 넣는 것을 막기 위함).
    var services by remember {
        mutableStateOf(
            ServiceCatalogStore.readAll(
                filesDir,
                if (AddrIniStore.readMasked(filesDir) != null) AddrIniStore.readRaw(filesDir) else null
            )
        )
    }

    val statuses = remember { mutableStateMapOf<String, CheckStatus>() }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("등록 현황", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { watchlist = WatchlistStore.readAll(filesDir) }) {
                    Text("새로고침")
                }
            }
            Spacer(Modifier.height(8.dp))
            watchlist.forEach { cat ->
                Text(cat.label, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (cat.items.isEmpty()) "등록된 항목 없음" else cat.items.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
        }
        item {
            Text("공공데이터포털 인증키", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            // 최초 설치자는 서비스키 자체가 없는 경우가 대부분이라, 키 입력란
            // 바로 위에 포털 로그인/회원가입 딥링크를 둔다(2026-10-09 "편의성"
            // 요청) - 아래 "서비스별 활용신청 바로가기"로 가도 결국 로그인부터
            // 해야 하니, 그 전 단계를 여기서 바로 열어준다. 둘 다 브라우저에서
            // 직접 열어 확인한 실제 주소(추측 아님) - 로그인은 www 도메인의
            // 공개 진입점, 회원가입은 거기서 이어지는 auth 서브도메인 주소.
            Text(
                "아직 공공데이터포털 계정이 없으신가요?",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.data.go.kr/uim/login/loginView.do")))
                }) { Text("포털 로그인") }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://auth.data.go.kr/sso/common-signup")))
                }) { Text("회원가입") }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                maskedKey?.let { "현재 저장된 키: $it" } ?: "저장된 키가 없습니다 (아직 조회 결과를 받을 수 없음)",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = newKeyInput,
                onValueChange = { newKeyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("새 서비스키 입력") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.getText()?.text?.let { newKeyInput = it }
                }) { Text("붙여넣기") }
                Button(onClick = {
                    val ok = AddrIniStore.save(filesDir, newKeyInput)
                    if (ok) {
                        maskedKey = AddrIniStore.readMasked(filesDir)
                        newKeyInput = ""
                        saveMessage = "저장했습니다. 엔진이 1초 안에 새 키를 자동으로 읽습니다(앱 재시작 불필요)."
                        statuses.clear()
                        services = ServiceCatalogStore.readAll(filesDir, maskedKey?.let { AddrIniStore.readRaw(filesDir) })
                    } else {
                        saveMessage = "저장 실패 - 키를 입력했는지 확인해주세요."
                    }
                }) { Text("저장") }
            }
            saveMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))

            Text("서비스별 활용신청 바로가기", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }

        items(services) { svc ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val status = statuses[svc.key] ?: CheckStatus.UNKNOWN
                val mark = when {
                    status == CheckStatus.OK -> "✅ "
                    status == CheckStatus.FAIL -> "❌ "
                    status == CheckStatus.CHECKING -> "⏳ "
                    svc.healthCheckUrl == null -> "➖ "
                    else -> ""
                }
                Text("$mark${svc.label}", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(svc.applyUrl))
                    context.startActivity(intent)
                }) { Text("활용신청") }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Divider()
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                val key = AddrIniStore.readRaw(filesDir)
                if (key.isNullOrBlank() || key == "<여기에 입력하세요>") {
                    saveMessage = "먼저 서비스키를 저장해주세요."
                    return@Button
                }
                val checkable = services.filter { it.healthCheckUrl != null }
                checkable.forEach { svc -> statuses[svc.key] = CheckStatus.CHECKING }
                runHealthChecks(checkable) { svcKey, ok -> statuses[svcKey] = if (ok) CheckStatus.OK else CheckStatus.FAIL }
            }) { Text("키 상태 점검") }
            Spacer(Modifier.height(4.dp))
            Text(
                "❌는 \"키 또는 활용신청을 확인하세요\"를 의미합니다 - 틀린 키와 " +
                    "특정 서비스만 활용신청을 안 한 경우를 현재는 구분하지 않습니다. " +
                    "➖는 아직 이 점검 기능을 지원하지 않는 서비스입니다.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** 서비스당 최소 1건(numOfRows=1 등)짜리 가벼운 조회로 키 인증 여부만 확인한다.
 *  외부 호출이므로 [AndroidHttpClient]를 DSL 엔진을 거치지 않고 직접 쓴다 -
 *  엔진 세션/채팅 UI를 전혀 건드리지 않는 순수 점검용 호출. 점검 URL 자체는
 *  이제 addr.ini의 health_check_path=로부터 [ServiceCatalogStore]가 조립한다.
 *
 *  하나씩 순차로만 호출한다(이전 호출의 콜백이 와야 다음 호출 시작) - 전부
 *  동시에 쏘면 [AndroidHttpClient]의 고정 스레드풀(4개)을 이 점검 하나가
 *  다 채워버려서, 그 사이 들어오는 엔진(rest.sce)발 HTTP 요청이 스레드를
 *  못 받고 밀리다가 응답이 전혀 안 오는 것처럼 보이는 현상이 실기기에서
 *  재현됐다(2026-10-11, 서비스 9개 동시 점검 직후 모든 채팅 조회가 먹통).
 *  순차 호출이면 이 점검 하나가 쓰는 스레드는 항상 최대 1개뿐이다. */
private fun runHealthChecks(targets: List<ServiceCatalogStore.ServiceEntry>, onResult: (String, Boolean) -> Unit) {
    fun runFrom(index: Int) {
        if (index >= targets.size) return
        val svc = targets[index]
        val url = svc.healthCheckUrl
        if (url == null) {
            runFrom(index + 1)
            return
        }
        AndroidHttpClient.request("GET", url, emptyMap(), null) { result ->
            // data.go.kr 에러 응답은 실측으로 확인된 공통 패턴상 "_ERROR"로
            //끝나는 errMsg를 담는다(SERVICE_KEY_IS_NOT_REGISTERED_ERROR 등,
            // 이번 세션에서 실제로 관측). 200이면서 이 패턴이 없으면 인증 통과로
            // 본다 - "활용신청 안 한 서비스"의 실제 에러 코드는 전부 활용신청이
            // 이미 끝난 키만 갖고 있어 실측하지 못했다(산출물에 명시).
            val ok = result.ok && !result.body.contains("_ERROR")
            onResult(svc.key, ok)
            runFrom(index + 1)
        }
    }
    runFrom(0)
}
