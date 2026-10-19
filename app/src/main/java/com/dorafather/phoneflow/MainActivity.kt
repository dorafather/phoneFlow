package com.dorafather.phoneflow

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.dorafather.phoneflow.net.AddrIniStore
import com.dorafather.phoneflow.net.ChatHistoryStore
import com.dorafather.phoneflow.net.ExRestAreaStore
import com.dorafather.phoneflow.net.FlowMessageRouter
import com.dorafather.phoneflow.net.IniParser
import com.dorafather.phoneflow.net.LawdCodeStore
import com.dorafather.phoneflow.net.ParkingNearbyStore
import com.dorafather.phoneflow.net.WatchlistStore
import com.dorafather.phoneflow.ui.theme.PhoneFlowBlueLight
import com.dorafather.phoneflow.ui.theme.PhoneFlowStripeGray
import com.dorafather.phoneflow.ui.theme.PhoneFlowTheme
import com.notebookflow.engine.FlowBridge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

private const val TAG = "phoneFlow/JNI"

data class ChatMessage(val text: String, val fromUser: Boolean)

private enum class Screen { CHAT, SETTINGS, PROVIDER_DETAIL, SCENARIO, LAWD_PICKER, KMA_PICKER, FESTIVAL_PICKER, HIRA_PICKER, EGEN_PICKER, EX_PICKER }

/**
 * Jetpack Compose 기반 채팅 UI. 기존 JNI 초기화(FlowBridge.nativeInit/
 * nativeSetCallback)는 그대로 재사용하고,
 * ACTION() 콜백 처리만 FlowMessageRouter(REST 통신 모듈의 진입점)로 옮겼다 -
 * MainActivity 자신은 더 이상 FlowCallback을 구현하지 않는다.
 *
 * 업무지침_phoneFlow_UI개선_명령어서랍_설정화면.md로 명령어 서랍(☰)과
 * 설정 화면(⚙)을 추가했다 - 채팅 입력/전송 로직(sendChatInput) 자체는
 * 전혀 바뀌지 않았다(서랍은 입력창 텍스트만 채워줄 뿐, 전송은 여전히
 * 사용자가 버튼을 눌러야 함).
 */
class MainActivity : ComponentActivity() {

    private val messages = mutableStateListOf<ChatMessage>()

    /** 모든 메시지 추가는 반드시 이 함수를 거친다 - 화면 상태(messages)와
     *  영구 저장(ChatHistoryStore)을 항상 같이 갱신하기 위함(2026-10-18
     *  "채팅 기록이 재설치/재시작마다 사라진다" 피드백 - 최근 100개까지는
     *  filesDir/chat_history.json에 남아있다가 다음 실행 때 복원된다). */
    private fun appendMessage(msg: ChatMessage) {
        messages.add(msg)
        if (messages.size > ChatHistoryStore.MAX_MESSAGES) {
            messages.removeAt(0)
        }
        ChatHistoryStore.save(filesDir, messages.map { ChatHistoryStore.StoredMessage(it.text, it.fromUser) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            Log.e(TAG, "[DIAG] 처리되지 않은 예외 (thread=${thread.name})", e)
            prevHandler?.uncaughtException(thread, e)
        }

        messages.addAll(
            ChatHistoryStore.load(filesDir).map { ChatMessage(text = it.text, fromUser = it.fromUser) }
        )

        FlowMessageRouter.setFinalMessageListener { json ->
            // ACTION() 콜백은 엔진 전용 네이티브 스레드(또는 AndroidHttpClient의
            // 워커 스레드)에서 올 수 있으므로, Compose 상태 변경은 반드시
            // runOnUiThread로 메인 스레드에 올린다.
            runOnUiThread {
                appendMessage(ChatMessage(text = extractDisplayText(json), fromUser = false))
            }
        }

        try {
            copyAssetScenarioFiles()
            FlowBridge.nativeSetCallback(FlowMessageRouter)
            FlowBridge.nativeInit(filesDir.absolutePath)
            Log.i(TAG, "FlowBridge.nativeInit() 호출 성공 (baseDir=${filesDir.absolutePath})")
        } catch (t: Throwable) {
            Log.e(TAG, "FlowBridge native 호출 실패", t)
            appendMessage(ChatMessage(text = "엔진 초기화 실패: ${t.message}", fromUser = false))
        }

        setContent {
            PhoneFlowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        messages = messages,
                        filesDir = filesDir,
                        onSend = ::sendChatInput,
                        onNearbyParkingResolved = ::handleNearbyParkingRequest
                    )
                }
            }
        }

        // 디버그 전용 - adb(uiautomator/input text)로 한글을 EditText에 직접 타이핑할
        // 수단이 없어(adb shell input text가 한글 KeyEvent 매핑이 없어 NPE로 실패함,
        // 실측 확인) 실기기 자동 검증을 위해 추가한 테스트 훅이다. am broadcast로
        // 텍스트를 주입하면 사용자가 직접 입력한 것과 동일하게 sendChatInput()을 탄다.
        // 운영 기능이 아니라 QA 보조 도구 - 명령 파싱/엔진 동작 경로는 전혀 건드리지 않는다.
        // ⚠️ debuggable 빌드(디버그 APK)에서만 등록 - 서명된 release 빌드에서는
        // ApplicationInfo.FLAG_DEBUGGABLE이 꺼져 있어 이 수신기 자체가 생성되지 않는다
        // (임의의 다른 앱이 엔진에 명령을 주입할 수 있는 통로이므로 release에는 노출 금지).
        val isDebuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) {
            registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val text = intent.getStringExtra("text") ?: return
                        Log.i(TAG, "[DEBUG_CHAT] am broadcast로 채팅 입력 주입: $text")
                        sendChatInput(text)
                    }
                },
                IntentFilter("com.dorafather.phoneflow.DEBUG_CHAT_INPUT"),
                Context.RECEIVER_EXPORTED
            )
        }
    }

    /**
     * rest.sce/addr.ini는 RUNFLOW()가 현재 작업 디렉터리(= nativeInit에 넘긴
     * baseDir, 즉 filesDir) 기준 상대경로로 읽는다. filesDir에 이미 파일이
     * 있으면 건드리지 않는다 - addr.ini는 사용자가 채운 서비스키와 채팅
     * 명령(관심종목 추가 등)이 함수.설정저장(SETINI)을 통해 실제로 써넣는
     * 대상이라, 매 실행마다 덮어쓰면 그 전부가 다음 실행 때 사라진다
     * (최초 설치 후 앱 실행 시 1회만 복사 - NotebookFlow의
     * addr.ini.template가 addr.ini를 최초 1회만 생성하고 이후 절대
     * 덮어쓰지 않는 것과 동일한 원칙).
     */
    private fun copyAssetScenarioFiles() {
        // lawd_codes.db(법정동코드 검색용, 읽기 전용 정적 데이터)도 같은 "최초 1회만
        // 복사" 규칙을 따른다 - addr.ini/rest.sce와 달리 이 파일은 앱이 직접 쓰지는
        // 않지만, 업데이트 시 새 데이터로 갱신하려면 앱 데이터 초기화가 필요하다는
        // 점은 동일한 제약(2026-10 rest.sce 교체 때와 같은 이유).
        for (name in listOf("addr.ini", "rest.sce", "commands.json", "lawd_codes.db", "kma_grid.db", "hira_region_codes.db", "ex_rest_areas.db")) {
            val dest = File(filesDir, name)
            if (dest.exists()) continue
            assets.open(name).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        Log.i(TAG, "시나리오(addr.ini/rest.sce) 준비 완료 -> ${filesDir.absolutePath}")
    }

    private fun sendChatInput(text: String) {
        appendMessage(ChatMessage(text = text, fromUser = true))
        val event = JSONObject().apply {
            put("이벤트명", "채팅입력")
            put("text", text)
        }
        FlowBridge.nativePushEvent(event.toString())
    }

    /**
     * "주차장 > 내 주변" 전용 경로(2026-10-19) - 다른 모든 "내 주변" 기능과
     * 달리 rest.sce를 거치지 않는다. PrkSttusInfo_v1엔 반경검색 파라미터가
     * 없고, 이 DSL 엔진엔 정렬/제곱근 함수가 없어 실제 거리순 정렬 자체를
     * rest.sce 안에서 할 수 없다(ParkingNearbyStore 주석 참고) - 그래서
     * GPS 권한 확인 후 좌표를 받으면(AppRoot) 바로 여기로 넘어와 사용자
     * 메시지를 먼저 echo하고, 지오코딩+실거리계산을 백그라운드에서 수행한
     * 뒤 결과를 봇 메시지로 바로 추가한다(전송 버튼을 누를 DSL 명령 자체가
     * 없으므로 - 계산된 최종 결과만 있을 뿐).
     */
    private fun handleNearbyParkingRequest(lat: Double, lon: Double) {
        appendMessage(ChatMessage(text = "주차장 내 주변", fromUser = true))
        lifecycleScope.launch(Dispatchers.IO) {
            val resultText = try {
                resolveNearbyParkingText(lat, lon)
            } catch (t: Throwable) {
                Log.e(TAG, "주차장 내 주변 조회 실패", t)
                "내 주변 주차장을 조회하지 못했습니다: ${t.message}"
            }
            withContext(Dispatchers.Main) {
                appendMessage(ChatMessage(text = resultText, fromUser = false))
            }
        }
    }

    private fun resolveNearbyParkingText(lat: Double, lon: Double): String {
        val fallbackGuide = "드로워 > 주차장 > 지역 선택에서 직접 검색해주세요."
        val address = try {
            @Suppress("DEPRECATION")
            Geocoder(this, Locale.KOREA).getFromLocation(lat, lon, 1)?.firstOrNull()
        } catch (t: Throwable) {
            Log.e(TAG, "역지오코딩 실패", t)
            null
        } ?: return "현재 위치를 지역으로 변환하지 못했습니다. $fallbackGuide"

        val sigunguName = address.subAdminArea ?: address.locality
            ?: return "현재 위치의 시/군/구를 확인하지 못했습니다. $fallbackGuide"
        val sidoName = address.adminArea

        // 동명 시군구가 전국에 여러 곳 있을 수 있어(중구 등), geocoder가 알려준
        // 시/도 이름으로 교차 확인한다(LawdPickerScreen과 달리 사용자가 직접
        // 고를 수 없는 자동 흐름이라 꼭 필요한 보정).
        val candidates = LawdCodeStore.search(filesDir, sigunguName, limit = 30)
        val match = candidates.firstOrNull { sidoName != null && it.fullName.startsWith(sidoName) }
            ?: candidates.firstOrNull()
            ?: return "\"$sigunguName\" 지역코드를 찾지 못했습니다. $fallbackGuide"

        val serviceKey = AddrIniStore.readRaw(filesDir, "K_DATA")
            ?: return "K_DATA 인증키가 설정되어 있지 않습니다. 설정 화면에서 먼저 키를 입력해주세요."
        val domain = IniParser.parse(File(filesDir, "addr.ini").readText())["PARKING"]?.get("domain")
            ?: "https://apis.data.go.kr/B553881/Parking_v1"

        val nearest = ParkingNearbyStore.findNearest(
            domain = domain,
            serviceKey = serviceKey,
            sidoCd = match.parentCode5.take(2),
            sigunguCd = match.parentCode5,
            userLat = lat,
            userLon = lon
        )
        if (nearest.isEmpty()) {
            return "${match.parentDisplayName} 지역에 위경도 정보가 있는 주차장을 찾지 못했습니다."
        }
        val lines = nearest.joinToString("\n") { p ->
            val distText = if (p.distanceMeters >= 1000) {
                String.format(Locale.KOREA, "%.1fkm", p.distanceMeters / 1000.0)
            } else {
                "${p.distanceMeters}m"
            }
            "${p.name} (${p.addr}) $distText / ${p.spaceCount}면 / ${p.feeName} / ${p.agentName}"
        }
        return "${match.parentDisplayName} 내 주변 주차장 (가까운 순 ${nearest.size}곳):\n$lines"
    }

    private fun extractDisplayText(json: JSONObject): String {
        // 핑 테스트 결과("봇응답" 이벤트, text 필드)와 그 외 메시지 모두
        // 안전하게 사람이 읽을 수 있는 형태로 보여준다.
        // rest.sce는 처리::/문장:: 한 줄 제약 때문에 소스에 실제 개행 문자를
        // 넣을 방법이 없어 여러 줄을 "|"로 이어 붙인다(모든 서비스 공통
        // 관례) - 그 구분자를 폰 화면에 그대로 보여주지 않고, 표시 직전에만
        // 실제 줄바꿈으로 바꿔준다(Compose Text는 기본으로 "\n"을 줄바꿈으로
        // 렌더링함) - rest.sce/엔진 쪽은 전혀 안 건드림.
        if (json.has("text")) return json.optString("text").replace("|", "\n")
        return json.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(
    messages: List<ChatMessage>,
    filesDir: File,
    onSend: (String) -> Unit,
    onNearbyParkingResolved: (Double, Double) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.CHAT) }
    // TextFieldValue로 들고 있어야 서랍에서 텍스트를 넣을 때 커서를 항상
    // 맨 끝에 둘 수 있다(업무지침 "커서를 맨 끝에 두고 사용자가 인자를
    // 채워 전송" 요구사항) - 일반 String 상태로는 커서 위치를 보장할 수 없다.
    var input by remember { mutableStateOf(TextFieldValue("")) }
    // EGEN_PICKER 화면 하나를 "응급실"/"당직병원" 둘이 같이 쓴다 - 드로워에서
    // 어느 쪽을 눌렀는지만 기억해뒀다가 고른 지역을 그 명령어 뒤에 붙인다.
    var egenPickerPrefix by remember { mutableStateOf("응급실 ") }
    // EX_PICKER 화면 하나를 "주유소 가격조회"/"휴게소 대표메뉴"/"휴게소 입점
    // 브랜드" 셋이 같이 쓴다(2026-10-13, 휴게소명 자동완성 요청) - EGEN_PICKER와
    // 동일한 prefix 패턴.
    var exPickerPrefix by remember { mutableStateOf("주유소 ") }
    // LAWD_PICKER 화면 하나를 "실거래가 지역추가"/"주차장" 둘이 같이 쓴다
    // (2026-10-19 주차장 기능 추가 - sigunguCd가 MOLIT과 같은 5자리 법정동코드
    // 체계임을 확인해 새 DB 없이 재사용) - EGEN_PICKER와 동일한 prefix 패턴.
    var lawdPickerPrefix by remember { mutableStateOf("실거래가 지역추가 ") }
    // PROVIDER_DETAIL 화면 하나를 모든 공급자(K_DATA/KOBIS/EX 등)가 같이 쓴다 -
    // 설정 화면에서 어느 공급자 카드를 눌렀는지만 기억해둔다(2026-10-12
    // "공급자별 인증키" 설계 - EGEN_PICKER의 prefix 패턴과 동일한 원리).
    var selectedProvider by remember { mutableStateOf("K_DATA") }

    // "행사 > 내 주변"(GPS 기반 locationBasedList2, 2026-10-09 추가) - 위치
    // 권한이 이미 있으면 바로 마지막 위치를 읽어 "주변행사 위도@경도 " 명령을
    // 채우고, 없으면 시스템 권한 요청 다이얼로그를 띄운 뒤 승인되면 이어서
    // 같은 동작을 한다. Play Services 등 새 의존성 없이 프레임워크
    // LocationManager.getLastKnownLocation만 쓴다(실시간 추적 불필요 -
    // 이미 기기가 최근에 구해둔 위치로 충분).
    val context = LocalContext.current
    fun fillNearbyFestivalCommand() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val best = lm.getProviders(true)
            .mapNotNull { provider ->
                try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }
            .minByOrNull { it.accuracy }
        if (best == null) {
            val insert = "주변행사 "
            input = TextFieldValue(insert, selection = TextRange(insert.length))
        } else {
            val insert = "주변행사 ${best.latitude}@${best.longitude} "
            input = TextFieldValue(insert, selection = TextRange(insert.length))
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) fillNearbyFestivalCommand() }
    fun requestNearbyFestival() {
        val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            fillNearbyFestivalCommand()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // "주유소 > 내 주변"(2026-10-12 추가) - 행사의 GPS 플로우와 완전히 같은
    // 패턴이지만, 외부 API에 좌표를 바로 넘기는 게 아니라 로컬 DB
    // (ex_rest_areas.db, ExRestAreaStore)에서 가장 가까운 5곳을 먼저
    // 골라 그 이름들을 "|"로 이어붙여 "주유소 내주변 이름1|이름2|..."
    // 명령으로 채운다 - rest.sce가 이 5개를 순회하며 각각 가격을 조회한다.
    fun fillNearbyGasCommand() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val best = lm.getProviders(true)
            .mapNotNull { provider ->
                try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }
            .minByOrNull { it.accuracy }
        if (best == null) {
            val insert = "주유소 "
            input = TextFieldValue(insert, selection = TextRange(insert.length))
            return
        }
        val nearest = ExRestAreaStore.nearest(filesDir, best.latitude, best.longitude, limit = 5)
        val insert = if (nearest.isEmpty()) {
            "주유소 "
        } else {
            "주유소 내주변 " + nearest.joinToString("|") { it.name }
        }
        input = TextFieldValue(insert, selection = TextRange(insert.length))
    }
    val locationPermissionLauncherGas = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) fillNearbyGasCommand() }
    fun requestNearbyGas() {
        val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            fillNearbyGasCommand()
        } else {
            locationPermissionLauncherGas.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // "휴게소 > 내 주변"(2026-10-12 추가) - fillNearbyGasCommand와 완전히
    // 같은 패턴(같은 ex_rest_areas.db, 같은 최근접 5곳 계산)이지만 명령
    // 접두어만 "메뉴 내주변"으로 다르다 - rest.sce의 EX메뉴근처 체인이
    // 각 이름당 대표메뉴 1건씩만 모아 보여준다.
    fun fillNearbyMenuCommand() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val best = lm.getProviders(true)
            .mapNotNull { provider ->
                try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }
            .minByOrNull { it.accuracy }
        if (best == null) {
            val insert = "메뉴 "
            input = TextFieldValue(insert, selection = TextRange(insert.length))
            return
        }
        val nearest = ExRestAreaStore.nearest(filesDir, best.latitude, best.longitude, limit = 5)
        val insert = if (nearest.isEmpty()) {
            "메뉴 "
        } else {
            "메뉴 내주변 " + nearest.joinToString("|") { it.name }
        }
        input = TextFieldValue(insert, selection = TextRange(insert.length))
    }
    val locationPermissionLauncherMenu = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) fillNearbyMenuCommand() }
    fun requestNearbyMenu() {
        val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            fillNearbyMenuCommand()
        } else {
            locationPermissionLauncherMenu.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // "주차장 > 내 주변"(2026-10-19 추가) - 좌표를 구하는 부분까지는 위
    // 세 기능과 동일한 패턴이지만, 그 다음이 다르다: 입력창에 명령을 채우는
    // 대신 좌표를 그대로 MainActivity(onNearbyParkingResolved)로 넘긴다 -
    // 실제 거리 계산을 위해 지오코딩+API 호출+정렬이 필요해(ParkingNearbyStore
    // 참고) 더 이상 "한 줄 채팅 명령"으로 표현할 수 없기 때문이다.
    fun fillNearbyParkingCommand() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val best = lm.getProviders(true)
            .mapNotNull { provider ->
                try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }
            .minByOrNull { it.accuracy }
        if (best == null) {
            val insert = "주차장 "
            input = TextFieldValue(insert, selection = TextRange(insert.length))
        } else {
            onNearbyParkingResolved(best.latitude, best.longitude)
        }
    }
    val locationPermissionLauncherParking = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) fillNearbyParkingCommand() }
    fun requestNearbyParking() {
        val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            fillNearbyParkingCommand()
        } else {
            locationPermissionLauncherParking.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                CommandDrawerContent(
                    filesDir = filesDir,
                    isDrawerOpen = drawerState.isOpen,
                    onCommandPicked = { insert ->
                        // "입력창에 이미 글자가 있어도 덮어쓴다"(업무지침 1절) -
                        // 이어붙이면 "주식 관심종목 추가 기상청 날씨"처럼 문자열이
                        // 엉킬 수 있어 항상 교체한다.
                        input = TextFieldValue(insert, selection = TextRange(insert.length))
                        screen = Screen.CHAT
                        scope.launch { drawerState.close() }
                    },
                    onSettingsPicked = {
                        screen = Screen.SETTINGS
                        scope.launch { drawerState.close() }
                    },
                    onScenarioPicked = {
                        screen = Screen.SCENARIO
                        scope.launch { drawerState.close() }
                    },
                    onLawdPickerRequested = { prefix ->
                        lawdPickerPrefix = prefix
                        screen = Screen.LAWD_PICKER
                        scope.launch { drawerState.close() }
                    },
                    onKmaPickerRequested = {
                        screen = Screen.KMA_PICKER
                        scope.launch { drawerState.close() }
                    },
                    onFestivalPickerRequested = {
                        screen = Screen.FESTIVAL_PICKER
                        scope.launch { drawerState.close() }
                    },
                    onNearbyFestivalRequested = {
                        requestNearbyFestival()
                        screen = Screen.CHAT
                        scope.launch { drawerState.close() }
                    },
                    onHiraPickerRequested = {
                        screen = Screen.HIRA_PICKER
                        scope.launch { drawerState.close() }
                    },
                    onEgenPickerRequested = { prefix ->
                        egenPickerPrefix = prefix
                        screen = Screen.EGEN_PICKER
                        scope.launch { drawerState.close() }
                    },
                    onExPickerRequested = { prefix ->
                        exPickerPrefix = prefix
                        screen = Screen.EX_PICKER
                        scope.launch { drawerState.close() }
                    },
                    onNearbyGasRequested = {
                        requestNearbyGas()
                        screen = Screen.CHAT
                        scope.launch { drawerState.close() }
                    },
                    onNearbyMenuRequested = {
                        requestNearbyMenu()
                        screen = Screen.CHAT
                        scope.launch { drawerState.close() }
                    },
                    onNearbyParkingRequested = {
                        requestNearbyParking()
                        screen = Screen.CHAT
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (screen == Screen.CHAT) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.zebra_face),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PhoneFlow")
                            }
                        } else {
                            Text(
                                when (screen) {
                                    Screen.SETTINGS -> "설정"
                                    Screen.PROVIDER_DETAIL -> "인증키 설정"
                                    Screen.SCENARIO -> "시나리오 설정"
                                    Screen.LAWD_PICKER -> "지역 검색"
                                    Screen.KMA_PICKER -> "지역 검색"
                                    Screen.FESTIVAL_PICKER -> "지역 검색"
                                    Screen.HIRA_PICKER -> "지역 검색"
                                    Screen.EGEN_PICKER -> "지역 검색"
                                    Screen.EX_PICKER -> "휴게소 검색"
                                    Screen.CHAT -> "PhoneFlow"
                                }
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "메뉴", tint = Color.White)
                        }
                    },
                    actions = {
                        // "메뉴트리를 꼭 거쳐야만 채팅으로 돌아갈 수 있어 불편하다"는
                        // 2026-10-18 피드백 - 채팅이 아닌 모든 화면(설정/시나리오
                        // 설정/피커 전부)에서 한 번에 채팅으로 바로 돌아갈 수 있게
                        // 흰색 동그란 말풍선 버튼을 둔다(카카오톡/텔레그램처럼 항상
                        // 대화창으로 복귀하는 길을 열어두는 디자인 참고).
                        if (screen != Screen.CHAT) {
                            ChatEntryButton(onClick = { screen = Screen.CHAT })
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (screen) {
                    Screen.CHAT -> ChatScreen(
                        messages = messages,
                        input = input,
                        onInputChange = { input = it },
                        onSend = { text ->
                            onSend(text)
                            input = TextFieldValue("")
                        }
                    )
                    Screen.SETTINGS -> SettingsScreen(filesDir = filesDir) { section ->
                        selectedProvider = section
                        screen = Screen.PROVIDER_DETAIL
                    }
                    Screen.PROVIDER_DETAIL -> ProviderSettingsScreen(filesDir = filesDir, section = selectedProvider)
                    Screen.SCENARIO -> ScenarioViewerScreen(filesDir = filesDir)
                    Screen.LAWD_PICKER -> LawdPickerScreen(filesDir = filesDir) { nameCodeToken ->
                        val insert = "$lawdPickerPrefix$nameCodeToken"
                        input = TextFieldValue(insert, selection = TextRange(insert.length))
                        screen = Screen.CHAT
                    }
                    Screen.KMA_PICKER -> KmaPickerScreen(filesDir = filesDir) { nameNxNyToken ->
                        // 기상청은 "지역추가"가 아니라 "지역 추가"(공백 있음) 문법이다
                        // (rest.sce 기존 관례 그대로 유지 - MOLIT과 다름).
                        val insert = "기상청 지역 추가 $nameNxNyToken"
                        input = TextFieldValue(insert, selection = TextRange(insert.length))
                        screen = Screen.CHAT
                    }
                    Screen.FESTIVAL_PICKER -> FestivalPickerScreen(filesDir = filesDir) { token ->
                        // 행사는 관심지역 등록이 없는 조회 전용 기능이라 "지역추가"가
                        // 아니라 "행사 [지역] [계약년월]" 1회성 조회 형태로 바로 채운다.
                        // 계약년월을 비워두고 사용자가 직접 입력하게 했었는데, "조회"/
                        // "내 주변"은 아무 입력 없이 바로 이번달이 나오는 것과 달리
                        // 이 경로만 "계약년월 형식을 이해하지 못했습니다"로 막혀서
                        // 드로워 메뉴 4갈래의 일관성이 깨져 있었다(2026-10-12 피드백) -
                        // 다른 경로들과 똑같이 이번달을 기본값으로 채워서 고르자마자
                        // 바로 조회 가능하게 통일했다(원하면 전송 전에 직접 수정 가능).
                        val thisMonth = java.text.SimpleDateFormat("yyyyMM", java.util.Locale.KOREA)
                            .format(java.util.Date())
                        val insert = "행사 $token $thisMonth"
                        input = TextFieldValue(insert, selection = TextRange(insert.length))
                        screen = Screen.CHAT
                    }
                    Screen.HIRA_PICKER -> HiraPickerScreen(filesDir = filesDir) { token ->
                        // 병원도 행사와 마찬가지로 관심지역 등록 없는 조회 전용 -
                        // "병원 [지역]" 형태로 채우면 바로 전송 가능(추가 인자 없음).
                        val insert = "병원 $token"
                        input = TextFieldValue(insert, selection = TextRange(insert.length))
                        screen = Screen.CHAT
                    }
                    Screen.EGEN_PICKER -> EgenPickerScreen(filesDir = filesDir) { token ->
                        // 응급실/당직병원 둘 다 여기로 오고, egenPickerPrefix로 어느
                        // 명령어였는지만 구분한다(둘 다 관심지역 등록 없는 조회 전용).
                        val insert = "$egenPickerPrefix$token"
                        input = TextFieldValue(insert, selection = TextRange(insert.length))
                        screen = Screen.CHAT
                    }
                    Screen.EX_PICKER -> ExRestAreaPickerScreen(filesDir = filesDir) { token ->
                        // 주유소 가격조회/휴게소 대표메뉴/휴게소 입점 브랜드 셋 다
                        // 여기로 오고, exPickerPrefix로 어느 명령어였는지만 구분한다.
                        val insert = "$exPickerPrefix$token"
                        input = TextFieldValue(insert, selection = TextRange(insert.length))
                        screen = Screen.CHAT
                    }
                }
            }
        }
    }
}

// "OO 조회" 리프를 더 펼치면, 그 카테고리에 지금 등록된 항목이 바로 하위
// 항목으로 나온다(addr.ini를 즉시 읽을 뿐, 보여주는 용도 자체는 rest.sce
// 변경이 없음 - 2026-10-03 "왜 복잡하게 생각하지?" 피드백: 등록된 항목
// 하나를 탭하면 그 항목용 "조회 문자열"이 입력창에 채워지면 그걸로 끝).
// 지역 3종(기상청/미세먼지/실거래가)은 이미 있던 "기상청 날씨 [지역]"/
// "미세먼지 [지역]"/"실거래가 [지역명] [계약년월]" 단건 조회 포맷을 그대로
// 재사용했다. 주식은 그런 단건 조회 명령이 rest.sce에 아예 없어서(기존
// "관심종목 조회"는 항상 전체 목록) "주식 시세 [종목명]" 명령을 rest.sce에
// 새로 추가했다(2026-10-03 "주식도 똑같이 해줘" 요청).
// 네 그룹 다 "조회"류 리프 하나가 등록된 항목을 펼쳐 보여주는 역할까지
// 겸한다(2026-10-12 "미세먼지도 주식처럼 조회 누르면 바로 등록 지역이
// 펼쳐지게 통일하자" 피드백) - 기존엔 주식만 그랬고 기상청/미세먼지/
// 실거래가는 "날씨"/"조회"가 기본지역 단건조회용 별도 리프였고 "지역 조회"가
// 따로 펼침 역할이었다(리프 4개, 불일치) - 이제 세 그룹 다 "지역 조회" 리프
// 자체를 없애고 기본 리프가 펼침 역할을 겸하도록 통일했다(리프 3개로 축소,
// 주식과 동일 구조).
private val LEAF_LABEL_TO_SUBTREE = mapOf(
    "기상청" to "날씨",
    "미세먼지" to "조회",
    "실거래가" to "조회",
    "주식" to "관심종목 조회",
)
private val ITEM_QUERY_TEMPLATES: Map<String, (String) -> String> = mapOf(
    "기상청" to { region -> "기상청 날씨 $region" },
    "미세먼지" to { region -> "미세먼지 $region" },
    "실거래가" to { region -> "실거래가 $region " }, // 계약년월은 사용자가 이어서 입력
    "주식" to { name -> "주식 시세 $name" }
)
// "지역 삭제"/"관심종목 삭제" 리프도 "지역 조회"와 동일한 패턴으로 등록된
// 항목을 펼쳐 보여주고, 탭하면 그 이름이 채워진 삭제 명령을 바로 넣어준다
// (2026-10-09 "이름 직접 입력하는 것보다 목록에서 선택하게 해달라" 요청 -
// 삭제할 땐 정확한 이름을 기억해 직접 타이핑해야 해서 불편했음).
private val DELETE_LEAF_LABEL = mapOf(
    "기상청" to "지역 삭제",
    "미세먼지" to "지역 삭제",
    "실거래가" to "지역 삭제",
    "주식" to "관심종목 삭제",
)
private val ITEM_DELETE_TEMPLATES: Map<String, (String) -> String> = mapOf(
    "기상청" to { region -> "기상청 지역 삭제 $region" },
    "미세먼지" to { region -> "미세먼지 지역 삭제 $region" },
    "실거래가" to { region -> "실거래가 지역삭제 $region" }, // MOLIT는 "지역삭제" 붙여쓰기
    "주식" to { name -> "주식 관심종목 삭제 $name" }
)

@Composable
private fun CommandDrawerContent(
    filesDir: File,
    // "지역 조회" 하위트리의 등록 항목 목록(WatchlistStore.itemNamesForGroup)이
    // addr.ini를 즉시 읽긴 하지만, 드로워를 닫았다 다시 열기만 해서는 그 값을
    // 읽는 LazyColumn item 블록이 재실행되지 않아(expanded/expandedQueries
    // 상태가 안 바뀌어 recomposition 트리거가 없음) 새로 등록한 지역이 안
    // 보이는 채로 멈춰 있었다(2026-10-08 전국 검색 기능 검증 중 발견 - 접었다
    // 펼치면 바로 반영되는 것으로 원인 확인). isDrawerOpen을 remember 키로
    // 써서, commands.json/addr.ini 기반 메뉴 목록(아래 groups)도 드로워를
    // 열 때마다 강제로 다시 읽는다(2026-10-10 "행사 메뉴가 TOUR 섹션 삭제
    // 후에도 그대로네?" 발견 - requires_section 필터링 참고).
    isDrawerOpen: Boolean,
    onCommandPicked: (String) -> Unit,
    onSettingsPicked: () -> Unit,
    onScenarioPicked: () -> Unit,
    onLawdPickerRequested: (String) -> Unit,
    onKmaPickerRequested: () -> Unit,
    onFestivalPickerRequested: () -> Unit,
    onNearbyFestivalRequested: () -> Unit,
    onHiraPickerRequested: () -> Unit,
    onEgenPickerRequested: (String) -> Unit,
    onExPickerRequested: (String) -> Unit,
    onNearbyGasRequested: () -> Unit,
    onNearbyMenuRequested: () -> Unit,
    onNearbyParkingRequested: () -> Unit
) {
    val groups = remember(isDrawerOpen) { loadAvailableCommandGroups(filesDir) }
    val expanded = remember { mutableStateListOf<String>() }
    val expandedQueries = remember { mutableStateListOf<String>() }

    Column(modifier = Modifier.fillMaxSize()) {
        // 메신저 앱들의 사이드 메뉴 상단 프로필 헤더를 참고한 브랜드 헤더
        // (2026-10-17 "얼룩말+파란색을 최대한 활용해서 귀엽게" 요청).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.zebra_face),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("PhoneFlow", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(
                    "내 손 안의 공공데이터",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
        LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            groups.forEach { group ->
                item(key = "group-${group.label}") {
                    val isOpen = expanded.contains(group.label)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .background(
                                if (isOpen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${group.icon ?: "•"}  ${group.label}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isOpen) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableCompat {
                                    if (isOpen) expanded.remove(group.label) else expanded.add(group.label)
                                }
                        )
                    }
                }
                if (expanded.contains(group.label)) {
                    group.items.forEach { cmd ->
                        val queryTemplate = ITEM_QUERY_TEMPLATES[group.label]
                        val deleteTemplate = ITEM_DELETE_TEMPLATES[group.label]
                        val isQueryLeaf = cmd.label == LEAF_LABEL_TO_SUBTREE[group.label] && queryTemplate != null
                        val isDeleteLeaf = cmd.label == DELETE_LEAF_LABEL[group.label] && deleteTemplate != null
                        val itemTemplate = if (isQueryLeaf) queryTemplate else deleteTemplate
                        if ((isQueryLeaf || isDeleteLeaf) && itemTemplate != null) {
                            val qKey = "${group.label}-${cmd.label}"
                            item(key = "item-$qKey") {
                                val qOpen = expandedQueries.contains(qKey)
                                Text(
                                    (if (qOpen) "▾ " else "▸ ") + cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat {
                                            if (qOpen) expandedQueries.remove(qKey) else expandedQueries.add(qKey)
                                        }
                                )
                            }
                            if (expandedQueries.contains(qKey)) {
                                val names = WatchlistStore.itemNamesForGroup(filesDir, group.label)
                                if (names.isEmpty()) {
                                    item(key = "item-$qKey-empty") {
                                        Text(
                                            "등록된 항목 없음",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 48.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                                        )
                                    }
                                } else {
                                    names.forEachIndexed { nameIdx, name ->
                                        item(key = "item-$qKey-$nameIdx-$name") {
                                            Text(
                                                name,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 48.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                                    .clickableCompat { onCommandPicked(itemTemplate(name)) }
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (group.label == "실거래가" && cmd.label == "지역 추가") {
                            // 전국 법정동 검색+선택 화면으로 보낸다(2026-10-08 요청) -
                            // 서울 25개구 하드코딩 대신 법정동코드 DB를 검색해서 고르면
                            // 입력창에 "이름@코드"가 채워진다(rest.sce가 바로 쓸 수 있는
                            // 형태 - LawdPickerScreen 주석 참고).
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onLawdPickerRequested("실거래가 지역추가 ") }
                                )
                            }
                        } else if (group.label == "주차장" && cmd.label == "지역 선택") {
                            // 주차장도 관심지역 등록 없는 조회 전용 - sigunguCd가 MOLIT과
                            // 같은 5자리 법정동코드 체계임을 확인해 LAWD_PICKER를 그대로
                            // 재사용한다(2026-10-19).
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onLawdPickerRequested("주차장 ") }
                                )
                            }
                        } else if (group.label == "주차장" && cmd.label == "내 주변") {
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onNearbyParkingRequested() }
                                )
                            }
                        } else if (group.label == "기상청" && cmd.label == "지역 추가") {
                            // 기상청 단기예보 격자(nx,ny) 전국 검색 화면으로 보낸다
                            // (2026-10-09 요청 - 법정동코드와 같은 행정구역코드 체계를
                            // 쓰는 걸 발견, MOLIT와 동일한 패턴으로 추가). 서울/부산 등
                            // 17개 주요도시 하드코딩은 간단한 입력 편의로 그대로 남겨뒀고,
                            // 이 화면은 그 외 전국 읍면동 단위 등록을 위한 것.
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onKmaPickerRequested() }
                                )
                            }
                        } else if (group.label == "행사" && cmd.label == "지역 선택") {
                            // 행사는 관심지역 등록 개념이 없는 조회 전용 기능이라 별도
                            // 왓치리스트/추가/삭제 없이, 전국 법정동 검색 화면으로
                            // 보내서 고른 지역으로 바로 1회성 조회 명령을 채운다
                            // (2026-10-09 한국관광공사 TourAPI 행사정보 추가 - 법정동
                            // 시도/시군구코드가 MOLIT과 같은 체계라 lawd_codes.db 재사용).
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onFestivalPickerRequested() }
                                )
                            }
                        } else if (group.label == "행사" && cmd.label == "내 주변") {
                            // GPS 기반 locationBasedList2 연동(2026-10-09 추가) - 위치
                            // 권한 확인/요청과 마지막 위치 읽기는 AppRoot의
                            // onNearbyFestivalRequested가 처리하고, 여기서는 그냥
                            // 호출만 한다.
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onNearbyFestivalRequested() }
                                )
                            }
                        } else if (group.label == "병원" && cmd.label == "지역 선택") {
                            // 병원도 행사와 같은 조회 전용 패턴(관심목록 없음) - 전국
                            // 법정동 검색 화면으로 보내서 고른 지역으로 바로 조회 명령을
                            // 채운다(2026-10-10 건강보험심사평가원 병원정보서비스 추가).
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onHiraPickerRequested() }
                                )
                            }
                        } else if (group.label == "응급실" && cmd.label == "가용병상 조회") {
                            // EGEN도 같은 지역 검색 화면 패턴 - "시도+시군구를 정확한
                            // 공식 명칭으로 직접 타이핑"이 너무 깨지기 쉬워서(2026-10-11
                            // "서울시"/"서울특별시" 단독 입력 모두 실패) 병원처럼 검색 후
                            // 선택으로 바꿨다. EgenPickerScreen이 "이름@시도명" 토큰을
                            // 만들어주면 rest.sce가 @로 분리해서 바로 쓴다.
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onEgenPickerRequested("응급실 ") }
                                )
                            }
                        } else if (group.label == "당직병원" && cmd.label == "지역 조회") {
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onEgenPickerRequested("당직병원 ") }
                                )
                            }
                        } else if (group.label == "약국" && cmd.label == "지역 조회") {
                            // 같은 EGEN 지역 검색 화면 재사용(국립중앙의료원 Q0/Q1도
                            // 공식 명칭을 그대로 요구해 당직병원과 동일한 함정이 있음).
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onEgenPickerRequested("약국 ") }
                                )
                            }
                        } else if (group.label == "주유소" && cmd.label == "내 주변") {
                            // GPS로 가장 가까운 휴게소 5곳을 로컬 DB(ex_rest_areas.db)에서
                            // 바로 계산한다(행사 "내 주변"과 달리 외부 API 호출 없음 -
                            // 2026-10-12 추가, AppRoot의 onNearbyGasRequested 참고).
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onNearbyGasRequested() }
                                )
                            }
                        } else if (group.label == "휴게소" && cmd.label == "내 주변") {
                            // "주유소 내 주변"과 같은 GPS+로컬 DB 계산이지만 "메뉴 내주변"
                            // 명령을 채운다(onNearbyMenuRequested, 2026-10-12 추가).
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onNearbyMenuRequested() }
                                )
                            }
                        } else if (group.label == "주유소" && cmd.label == "가격조회") {
                            // 휴게소명 자동완성 검색(ExRestAreaPickerScreen, 2026-10-13
                            // 추가 - "찾기가 너무 어렵다" 피드백) - EGEN_PICKER와 동일한
                            // prefix 패턴으로 onExPickerRequested에 접두어만 넘긴다.
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onExPickerRequested("주유소 ") }
                                )
                            }
                        } else if (group.label == "휴게소" && cmd.label == "대표메뉴") {
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onExPickerRequested("메뉴 ") }
                                )
                            }
                        } else if (group.label == "휴게소" && cmd.label == "전체메뉴") {
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onExPickerRequested("전체메뉴 ") }
                                )
                            }
                        } else if (group.label == "휴게소" && cmd.label == "입점 브랜드") {
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onExPickerRequested("브랜드 ") }
                                )
                            }
                        } else if (group.label == "휴게소" && cmd.label == "편의시설") {
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onExPickerRequested("편의시설 ") }
                                )
                            }
                        } else if (group.label == "휴게소" && cmd.label == "테마휴게소") {
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onExPickerRequested("테마 ") }
                                )
                            }
                        } else if (group.label == "휴게소" && cmd.label == "이벤트") {
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onExPickerRequested("이벤트 ") }
                                )
                            }
                        } else {
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onCommandPicked(cmd.insert) }
                                )
                            }
                        }
                    }
                }
            }
        }
        androidx.compose.material3.Divider()
        Text(
            "⚙ 설정",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .clickableCompat { onSettingsPicked() }
        )
        Text(
            "📄 시나리오 설정",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .clickableCompat { onScenarioPicked() }
        )
    }
}

private fun Modifier.clickableCompat(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

/** 흰색 동그란 말풍선 버튼 - 채팅이 아닌 화면 상단에서 채팅으로 한 번에
 *  돌아가는 바로가기(2026-10-18 요청, 메뉴트리를 거치지 않고도 진입). */
@Composable
private fun ChatEntryButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        color = Color.White
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("💬")
        }
    }
}

@Composable
private fun ChatScreen(
    messages: List<ChatMessage>,
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    onSend: (String) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    placeholder = { Text("메시지를 입력하세요 (예: 핑)") },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = PhoneFlowStripeGray,
                        unfocusedContainerColor = PhoneFlowStripeGray,
                        disabledContainerColor = PhoneFlowStripeGray,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            val text = input.text.trim()
                            if (text.isNotEmpty()) {
                                onSend(text)
                            }
                        },
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("➤", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages) { msg ->
                ChatBubble(msg)
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage) {
    val alignment = if (msg.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.align(alignment),
            verticalAlignment = Alignment.Bottom
        ) {
            if (!msg.fromUser) {
                Image(
                    painter = painterResource(id = R.drawable.zebra_face),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(PhoneFlowBlueLight)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Surface(
                modifier = Modifier.padding(4.dp),
                color = if (msg.fromUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = msg.text,
                    color = if (msg.fromUser) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
