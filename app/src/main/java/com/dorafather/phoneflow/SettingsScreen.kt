package com.dorafather.phoneflow

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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

private enum class CheckStatus { UNKNOWN, CHECKING, OK, FAIL }

/**
 * 설정 최상위 화면 - "공급자 목록"(인증키를 발급하는 기관 단위 -
 * 공공데이터포털/영화진흥위원회/한국도로공사 등)을 보여준다. 공급자를
 * 탭하면 [ProviderSettingsScreen]으로 들어가 그 공급자의 인증키 입력/
 * 서비스별 활용신청/키 상태 점검을 다룬다(2026-10-12 "인증키 입력도
 * 서비스별 활용신청도 전부 공공데이터포털의 하위다, 그 위에 상위 트리가
 * 있어야 한다"는 지적으로 분리됨 - 예전엔 이 화면 하나에 전부 들어있었다).
 * "등록 현황"(관심종목/관심지역 목록) 섹션은 2026-10-18 "일부만 나오는데
 * 지금 UI 컨셉에 안 맞는다"는 피드백으로 제거했다 - 등록 항목 확인은
 * 각 기능의 "조회" 명령(예: "주식 관심종목 조회")으로 하면 된다.
 */
@Composable
fun SettingsScreen(filesDir: java.io.File, onProviderPicked: (String) -> Unit) {
    val providers = remember { AddrIniStore.readProviders(filesDir) }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        item {
            Text("공급자", style = MaterialTheme.typography.titleMedium)
            Text(
                "인증키를 발급하는 기관 단위입니다. 탭하면 그 기관의 인증키 입력/서비스별 활용신청을 볼 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
        }

        items(providers) { provider ->
            Column(
                modifier = Modifier.fillMaxWidth()
                    .clickable { onProviderPicked(provider.section) }
                    .padding(vertical = 10.dp)
            ) {
                Text(provider.label, style = MaterialTheme.typography.bodyLarge)
                val masked = AddrIniStore.readMasked(filesDir, provider.section)
                Text(
                    masked?.let { "저장된 키: $it" } ?: "저장된 키 없음",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Divider()
        }
    }
}

/** 공급자 하나의 상세 화면 - 인증키 입력 + 서비스별 활용신청 바로가기 + 키 상태 점검.
 *  예전 SettingsScreen 전체 내용을 공급자 단위로 스코프만 좁혀 그대로 재사용한 것. */
@Composable
fun ProviderSettingsScreen(filesDir: java.io.File, section: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val provider = remember { AddrIniStore.readProviders(filesDir).firstOrNull { it.section == section } }
    var maskedKey by remember { mutableStateOf(AddrIniStore.readMasked(filesDir, section)) }
    var newKeyInput by remember { mutableStateOf("") }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    fun currentProviderKeys(): Map<String, String?> =
        AddrIniStore.readProviders(filesDir).associate { it.section to AddrIniStore.readRaw(filesDir, it.section) }

    var services by remember {
        mutableStateOf(
            ServiceCatalogStore.readAll(filesDir, currentProviderKeys()).filter { it.provider == section }
        )
    }

    val statuses = remember { mutableStateMapOf<String, CheckStatus>() }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        item {
            Text(provider?.label ?: section, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (provider?.loginUrl != null || provider?.signupUrl != null) {
                Text(
                    "아직 이 공급자 계정이 없으신가요?",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    provider.loginUrl?.let { url ->
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }) { Text("포털 로그인") }
                    }
                    provider.signupUrl?.let { url ->
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }) { Text("회원가입") }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
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
                    val ok = AddrIniStore.save(filesDir, section, newKeyInput)
                    if (ok) {
                        maskedKey = AddrIniStore.readMasked(filesDir, section)
                        newKeyInput = ""
                        saveMessage = "저장했습니다. 엔진이 1초 안에 새 키를 자동으로 읽습니다(앱 재시작 불필요)."
                        statuses.clear()
                        services = ServiceCatalogStore.readAll(filesDir, currentProviderKeys()).filter { it.provider == section }
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
                if (!AddrIniStore.hasRealKey(filesDir, section)) {
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
