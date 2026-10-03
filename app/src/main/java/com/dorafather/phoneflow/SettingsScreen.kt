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
import java.util.Calendar
import java.util.Locale

/** 5개 공공데이터 서비스의 활용신청 상세 페이지. NotebookFlow 작업 중
 *  직접 확인한 실제 주소(업무지침 원문 그대로, 앱에 박기 전 한 번 더
 *  열어 확인함). */
private data class ServiceInfo(
    val key: String,
    val label: String,
    val applyUrl: String
)

private val SERVICES = listOf(
    ServiceInfo("KRX", "주식(KRX)", "https://www.data.go.kr/data/15094808/openapi.do"),
    ServiceInfo("KMA", "기상청(KMA)", "https://www.data.go.kr/data/15084084/openapi.do"),
    ServiceInfo("KECO", "미세먼지(KECO)", "https://www.data.go.kr/data/15073861/openapi.do"),
    ServiceInfo("KMA_SPCD", "공휴일(KMA_SPCD)", "https://www.data.go.kr/data/15012690/openapi.do"),
    ServiceInfo("MOLIT", "실거래가(MOLIT)", "https://www.data.go.kr/data/15126469/openapi.do"),
)

private enum class CheckStatus { UNKNOWN, CHECKING, OK, FAIL }

@Composable
fun SettingsScreen(filesDir: java.io.File) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var maskedKey by remember { mutableStateOf(AddrIniStore.readMasked(filesDir)) }
    var newKeyInput by remember { mutableStateOf("") }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    val statuses = remember { mutableStateMapOf<String, CheckStatus>() }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        item {
            Text("공공데이터포털 인증키", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
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

        items(SERVICES) { svc ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val status = statuses[svc.key] ?: CheckStatus.UNKNOWN
                val mark = when (status) {
                    CheckStatus.OK -> "✅ "
                    CheckStatus.FAIL -> "❌ "
                    CheckStatus.CHECKING -> "⏳ "
                    CheckStatus.UNKNOWN -> ""
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
                SERVICES.forEach { svc -> statuses[svc.key] = CheckStatus.CHECKING }
                runHealthChecks(key) { svcKey, ok -> statuses[svcKey] = if (ok) CheckStatus.OK else CheckStatus.FAIL }
            }) { Text("키 상태 점검") }
            Spacer(Modifier.height(4.dp))
            Text(
                "❌는 \"키 또는 활용신청을 확인하세요\"를 의미합니다 - 틀린 키와 " +
                    "특정 서비스만 활용신청을 안 한 경우를 현재는 구분하지 않습니다.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** 서비스당 최소 1건(numOfRows=1 등)짜리 가벼운 조회로 키 인증 여부만 확인한다.
 *  외부 호출이므로 [AndroidHttpClient]를 DSL 엔진을 거치지 않고 직접 쓴다 -
 *  엔진 세션/채팅 UI를 전혀 건드리지 않는 순수 점검용 호출. */
private fun runHealthChecks(serviceKey: String, onResult: (String, Boolean) -> Unit) {
    val enc = java.net.URLEncoder.encode(serviceKey, "UTF-8")
    val cal = Calendar.getInstance()
    val thisYear = cal.get(Calendar.YEAR)
    cal.add(Calendar.DAY_OF_MONTH, -1)
    val yesterday = String.format(Locale.US, "%04d%02d%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    val molitCal = Calendar.getInstance().apply { add(Calendar.MONTH, -2) }
    val molitYm = String.format(Locale.US, "%04d%02d", molitCal.get(Calendar.YEAR), molitCal.get(Calendar.MONTH) + 1)

    val checks = listOf(
        "KRX" to "https://apis.data.go.kr/1160100/GetStockSecuritiesInfoService_V2/getStockPriceInfo_V2?serviceKey=$enc&resultType=json&numOfRows=1&pageNo=1&likeSrtnCd=005930",
        "KMA" to "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst?serviceKey=$enc&pageNo=1&numOfRows=1&dataType=JSON&base_date=$yesterday&base_time=0200&nx=60&ny=127",
        "KECO" to "https://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getCtprvnRltmMesureDnsty?serviceKey=$enc&returnType=json&numOfRows=1&pageNo=1&sidoName=서울&ver=1.5",
        "KMA_SPCD" to "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getHoliDeInfo?ServiceKey=$enc&pageNo=1&numOfRows=1&solYear=$thisYear&solMonth=01&_type=json",
        "MOLIT" to "https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade?serviceKey=$enc&LAWD_CD=11680&DEAL_YMD=$molitYm&pageNo=1&numOfRows=1&_type=json",
    )

    checks.forEach { (svcKey, url) ->
        AndroidHttpClient.request("GET", url, emptyMap(), null) { result ->
            // data.go.kr 에러 응답은 실측으로 확인된 공통 패턴상 "_ERROR"로
            //끝나는 errMsg를 담는다(SERVICE_KEY_IS_NOT_REGISTERED_ERROR 등,
            // 이번 세션에서 실제로 관측). 200이면서 이 패턴이 없으면 인증 통과로
            // 본다 - "활용신청 안 한 서비스"의 실제 에러 코드는 전부 활용신청이
            // 이미 끝난 키만 갖고 있어 실측하지 못했다(산출물에 명시).
            val ok = result.ok && !result.body.contains("_ERROR")
            onResult(svcKey, ok)
        }
    }
}
