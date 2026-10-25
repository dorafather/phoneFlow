package com.dorafather.phoneflow

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.dorafather.phoneflow.net.ExDetailStore
import com.dorafather.phoneflow.net.ExRestAreaStore
import com.dorafather.phoneflow.net.FlowMessageRouter
import com.dorafather.phoneflow.net.IniParser
import com.dorafather.phoneflow.net.LawdCodeStore
import com.dorafather.phoneflow.net.ParkingNearbyStore
import com.dorafather.phoneflow.net.TourNearbyStore
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

data class ChatMessage(val text: String, val fromUser: Boolean, val markers: List<MapPin>? = null)

private enum class Screen { CHAT, SETTINGS, PROVIDER_DETAIL, SCENARIO, LAWD_PICKER, KMA_PICKER, FESTIVAL_PICKER, HIRA_PICKER, EGEN_PICKER, EX_PICKER, MAP, MAP_BROWSER }

/** "내 주변" 응답 하나가 만드는 텍스트와 지도 핀을 함께 돌려주기 위한 결과 타입
 *  (2026-10-25 "지도로 보기" 추가 - 주차장류 함수들이 String 대신 이걸 반환). */
private data class NearbyResult(val text: String, val markers: List<MapPin>)

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

    // "주유소/메뉴 > 내 주변"은 입력창을 채우기만 하고 전송은 사용자가 직접
    // 눌러야 하는 패턴이라(행사와 동일, 주차장류와 다름) 좌표를 아는 시점
    // (ExRestAreaStore 로컬 조회 직후)과 응답이 오는 시점 사이에 간격이
    // 있다 - 그 사이 마커를 들고 있는 임시 저장소(2026-10-25 "지도로 보기"
    // 추가). stashNearbyMarkers로 채워두고, 실제로 그 문구 그대로 전송됐을
    // 때만(sendChatInput에서 텍스트 비교) 다음 봇 응답에 붙인다.
    private var pendingNearbyMarkers: List<MapPin>? = null
    private var pendingNearbyMarkersText: String? = null

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
                val markers = extractMarkers(json) ?: pendingNearbyMarkers
                pendingNearbyMarkers = null
                pendingNearbyMarkersText = null
                appendMessage(ChatMessage(text = extractDisplayText(json), fromUser = false, markers = markers))
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
                        onNearbyParkingResolved = ::handleNearbyParkingRequest,
                        onNearbyParkingFreeResolved = ::handleNearbyParkingFreeRequest,
                        onNearbyParkingPaidResolved = ::handleNearbyParkingPaidRequest,
                        onNearbyParkingLocationFailed = ::handleNearbyParkingLocationFailed,
                        onParkingCheapestResolved = ::handleParkingCheapestRequest,
                        onNearbyMarkersComputed = ::stashNearbyMarkers,
                        onFetchFestivalPins = ::fetchFestivalPins,
                        onFetchGasPins = ::fetchGasPins,
                        onFetchMenuPins = ::fetchMenuPins,
                        onFetchParkingPins = ::fetchParkingPins
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
        // 보류 중인 마커가 이번에 보내는 문구와 다른 명령이면(사용자가 채워진
        // 텍스트를 편집했거나 다른 명령을 입력함) 버린다 - 엉뚱한 응답에
        // 마커가 잘못 붙는 걸 막기 위함.
        if (text != pendingNearbyMarkersText) {
            pendingNearbyMarkers = null
            pendingNearbyMarkersText = null
        }
        val event = JSONObject().apply {
            put("이벤트명", "채팅입력")
            put("text", text)
        }
        FlowBridge.nativePushEvent(event.toString())
    }

    private fun stashNearbyMarkers(commandText: String, markers: List<MapPin>) {
        pendingNearbyMarkersText = commandText
        pendingNearbyMarkers = markers
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
    /**
     * requestFreshLocation()이 끝내 위치를 구하지 못했을 때(2026-10-20
     * "회사에선 실패" 리포트 이후 추가) - 예전엔 입력창에 "주차장 "만
     * 조용히 채워서 사용자가 "눌렀는데 아무 반응이 없다"고 느끼기 쉬웠다.
     * 이제는 왜 안 됐는지와 대안을 바로 봇 메시지로 알려준다.
     */
    private fun handleNearbyParkingLocationFailed(label: String) {
        appendMessage(ChatMessage(text = label, fromUser = true))
        appendMessage(
            ChatMessage(
                text = "현재 위치를 확인하지 못했습니다(실내 등 GPS/네트워크 신호가 약하면 발생할 수 있어요). " +
                    "잠시 후 다시 시도하시거나, 드로워 > 주차장 > 지역 선택에서 직접 검색해주세요.",
                fromUser = false
            )
        )
    }

    /**
     * "주차장" 드로워의 내 주변/무료만/유료만/저렴한 순이 전부 공유하는
     * GPS -> 지역코드 변환(2026-10-24, "전부 GPS 기준으로 바꿔달라" 요청으로
     * 네 기능이 전부 이 함수를 쓰게 통일했다). 경기도 하남시처럼 "도 > 시/군 >
     * 동" 구조인 지역은 subAdminArea에 시/군 이름이 들어오지만, 서울/부산 등
     * 특별시·광역시(구 바로 아래가 동이라 중간 단계가 없음)는 subAdminArea/
     * locality가 둘 다 비고 대신 subLocality에 구 이름이 들어온다(2026-10-21
     * 실측 확인 - "서울특별시 강남구" 주소에서 subAdminArea=null, locality=
     * null, subLocality=강남구였다). 동명 시군구가 전국에 여러 곳 있을 수
     * 있어(중구 등) geocoder가 알려준 시/도 이름으로 교차 확인한다
     * (LawdPickerScreen과 달리 사용자가 직접 고를 수 없는 자동 흐름이라
     * 꼭 필요한 보정).
     */
    private fun resolveParkingRegion(lat: Double, lon: Double): LawdCodeStore.LawdMatch? {
        val address = try {
            @Suppress("DEPRECATION")
            Geocoder(this, Locale.KOREA).getFromLocation(lat, lon, 1)?.firstOrNull()
        } catch (t: Throwable) {
            Log.e(TAG, "역지오코딩 실패", t)
            null
        } ?: return null
        Log.i(
            LOCATION_TAG,
            "지오코딩 결과 - adminArea=${address.adminArea}, subAdminArea=${address.subAdminArea}, " +
                "locality=${address.locality}, subLocality=${address.subLocality}, " +
                "featureName=${address.featureName}, addressLine0=${address.getAddressLine(0)}"
        )
        val sigunguName = address.subAdminArea ?: address.locality ?: address.subLocality ?: return null
        val sidoName = address.adminArea
        val candidates = LawdCodeStore.search(filesDir, sigunguName, limit = 30)
        return candidates.firstOrNull { sidoName != null && it.fullName.startsWith(sidoName) }
            ?: candidates.firstOrNull()
    }

    private fun parkingRegionFailedText(): String =
        "현재 위치를 지역으로 확인하지 못했습니다. 드로워 > 주차장 > 지역 선택에서 직접 검색해주세요."

    private fun formatDistance(meters: Int): String =
        if (meters >= 1000) String.format(Locale.KOREA, "%.1fkm", meters / 1000.0) else "${meters}m"

    private fun handleNearbyParkingRequest(lat: Double, lon: Double) {
        appendMessage(ChatMessage(text = "주차장 내 주변", fromUser = true))
        lifecycleScope.launch(Dispatchers.IO) {
            val result = try {
                resolveNearbyParkingText(lat, lon, feeFilter = null, label = "")
            } catch (t: Throwable) {
                Log.e(TAG, "주차장 내 주변 조회 실패", t)
                NearbyResult("내 주변 주차장을 조회하지 못했습니다: ${t.message}", emptyList())
            }
            withContext(Dispatchers.Main) {
                appendMessage(ChatMessage(text = result.text, fromUser = false, markers = result.markers.ifEmpty { null }))
            }
        }
    }

    /** "주차장 > 무료만 보기"(2026-10-24, GPS 기준으로 전환) - 거리순 + 무료만. */
    private fun handleNearbyParkingFreeRequest(lat: Double, lon: Double) {
        appendMessage(ChatMessage(text = "주차장 무료만 보기 (내 주변)", fromUser = true))
        lifecycleScope.launch(Dispatchers.IO) {
            val result = try {
                resolveNearbyParkingText(lat, lon, feeFilter = 1, label = "무료 ")
            } catch (t: Throwable) {
                Log.e(TAG, "주차장 무료만 보기 조회 실패", t)
                NearbyResult("내 주변 무료 주차장을 조회하지 못했습니다: ${t.message}", emptyList())
            }
            withContext(Dispatchers.Main) {
                appendMessage(ChatMessage(text = result.text, fromUser = false, markers = result.markers.ifEmpty { null }))
            }
        }
    }

    /** "주차장 > 유료만 보기"(2026-10-24, GPS 기준으로 전환) - 거리순 + 유료만. */
    private fun handleNearbyParkingPaidRequest(lat: Double, lon: Double) {
        appendMessage(ChatMessage(text = "주차장 유료만 보기 (내 주변)", fromUser = true))
        lifecycleScope.launch(Dispatchers.IO) {
            val result = try {
                resolveNearbyParkingText(lat, lon, feeFilter = 2, label = "유료 ")
            } catch (t: Throwable) {
                Log.e(TAG, "주차장 유료만 보기 조회 실패", t)
                NearbyResult("내 주변 유료 주차장을 조회하지 못했습니다: ${t.message}", emptyList())
            }
            withContext(Dispatchers.Main) {
                appendMessage(ChatMessage(text = result.text, fromUser = false, markers = result.markers.ifEmpty { null }))
            }
        }
    }

    /** feeFilter: null=전체, 1=무료만, 2=유료만. label은 "무료 "/"유료 "처럼 결과 문구 접두어. */
    private fun resolveNearbyParkingText(lat: Double, lon: Double, feeFilter: Int?, label: String): NearbyResult {
        val match = resolveParkingRegion(lat, lon) ?: return NearbyResult(parkingRegionFailedText(), emptyList())

        val serviceKey = AddrIniStore.readRaw(filesDir, "K_DATA")
            ?: return NearbyResult("K_DATA 인증키가 설정되어 있지 않습니다. 설정 화면에서 먼저 키를 입력해주세요.", emptyList())
        val domain = IniParser.parse(File(filesDir, "addr.ini").readText())["PARKING"]?.get("domain")
            ?: "https://apis.data.go.kr/B553881/Parking_v1"

        val nearest = ParkingNearbyStore.findNearest(
            domain = domain,
            serviceKey = serviceKey,
            sidoCd = match.parentCode5.take(2),
            sigunguCd = match.parentCode5,
            userLat = lat,
            userLon = lon,
            feeFilter = feeFilter
        )
        if (nearest.isEmpty()) {
            return NearbyResult("${match.parentDisplayName} 지역에 위경도 정보가 있는 ${label}주차장을 찾지 못했습니다.", emptyList())
        }
        val lines = nearest.joinToString("\n") { p ->
            "${p.name} (${p.addr}) ${formatDistance(p.distanceMeters)} / ${p.spaceCount}면 / ${p.feeName} / ${p.agentName}"
        }
        val markers = nearest.map { p ->
            MapPin(name = p.name, lat = p.lat, lon = p.lon, subtitle = "${formatDistance(p.distanceMeters)} / ${p.feeName}")
        }
        return NearbyResult("${match.parentDisplayName} 내 주변 ${label}주차장 (가까운 순 ${nearest.size}곳):\n$lines", markers)
    }

    /**
     * "주차장 > 저렴한 순"(2026-10-19 추가, 2026-10-24 GPS 기준으로 전환) -
     * 기본요금 비교엔 정렬이 필요하고 요금은 시설정보가 아니라 운영정보
     * (PrkOprInfo_v1)에만 있어 두 오퍼레이션을 prk_center_id로 조인해야
     * 한다(ParkingNearbyStore.findCheapest 참고) - 내 주변과 같은 이유로
     * rest.sce를 거치지 않는다. 지역 확인은 resolveParkingRegion으로
     * 다른 세 기능과 동일하게 GPS에서 바로 얻는다.
     */
    private fun handleParkingCheapestRequest(lat: Double, lon: Double) {
        appendMessage(ChatMessage(text = "주차장 저렴한 순 (내 주변)", fromUser = true))
        lifecycleScope.launch(Dispatchers.IO) {
            val result = try {
                resolveParkingCheapestText(lat, lon)
            } catch (t: Throwable) {
                Log.e(TAG, "주차장 저렴한 순 조회 실패", t)
                NearbyResult("저렴한 순 주차장을 조회하지 못했습니다: ${t.message}", emptyList())
            }
            withContext(Dispatchers.Main) {
                appendMessage(ChatMessage(text = result.text, fromUser = false, markers = result.markers.ifEmpty { null }))
            }
        }
    }

    private fun resolveParkingCheapestText(lat: Double, lon: Double): NearbyResult {
        val match = resolveParkingRegion(lat, lon) ?: return NearbyResult(parkingRegionFailedText(), emptyList())

        val serviceKey = AddrIniStore.readRaw(filesDir, "K_DATA")
            ?: return NearbyResult("K_DATA 인증키가 설정되어 있지 않습니다. 설정 화면에서 먼저 키를 입력해주세요.", emptyList())
        val domain = IniParser.parse(File(filesDir, "addr.ini").readText())["PARKING"]?.get("domain")
            ?: "https://apis.data.go.kr/B553881/Parking_v1"

        val cheapest = ParkingNearbyStore.findCheapest(
            domain = domain,
            serviceKey = serviceKey,
            sidoCd = match.parentCode5.take(2),
            sigunguCd = match.parentCode5
        )
        if (cheapest.isEmpty()) {
            return NearbyResult("${match.parentDisplayName} 지역에 요금 정보가 있는 주차장을 찾지 못했습니다.", emptyList())
        }
        val lines = cheapest.joinToString("\n") { p ->
            val feeText = if (p.baseChargeWon == 0) "무료" else "${p.baseChargeWon}원~"
            "${p.name} (${p.addr}) $feeText / ${p.spaceCount}면 / ${p.feeName} / ${p.agentName}"
        }
        // PrkOprInfo_v1(요금)엔 좌표가 없어 PrkSttusInfo_v1(시설정보)쪽 좌표가
        // 빠진 항목은 목록엔 남기되 핀에서만 제외한다(ParkingNearbyStore의
        // CheapestParking.lat/lon이 nullable인 이유).
        val markers = cheapest.mapNotNull { p ->
            val pLat = p.lat ?: return@mapNotNull null
            val pLon = p.lon ?: return@mapNotNull null
            val feeText = if (p.baseChargeWon == 0) "무료" else "${p.baseChargeWon}원~"
            MapPin(name = p.name, lat = pLat, lon = pLon, subtitle = feeText)
        }
        return NearbyResult("${match.parentDisplayName} 내 주변 주차장 요금 낮은 순 ${cheapest.size}곳:\n$lines", markers)
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

    /**
     * "지도" 체크박스 브라우저(2026-10-25 추가) 전용 카테고리별 조회 함수
     * 4개 - MapBrowserScreen에 콜백으로 넘겨준다. 전부 Dispatchers.IO에서
     * 호출됨을 전제로 한다(MapBrowserScreen의 scope.launch(Dispatchers.IO)).
     */
    private suspend fun fetchFestivalPins(lat: Double, lon: Double): List<MapPin> {
        val serviceKey = AddrIniStore.readRaw(filesDir, "K_DATA") ?: return emptyList()
        val domain = IniParser.parse(File(filesDir, "addr.ini").readText())["TOUR"]?.get("domain")
            ?: "https://apis.data.go.kr/B551011/KorService2"
        return TourNearbyStore.findNearest(domain, serviceKey, lat, lon).map { f ->
            // locationBasedList2엔 일정/장소가 없어 contentId로 상세를 한 번 더
            // 호출한다(2026-10-25 "행사 상세정보" 요청, TourNearbyStore.fetchDetail
            // 주석 참고 - 핀 하나당 1회, N+1).
            val detail = try {
                TourNearbyStore.fetchDetail(domain, serviceKey, f.contentId)
            } catch (_: Throwable) {
                null
            }
            val lines = mutableListOf("주소: ${f.addr}", "거리: ${formatDistance(f.distanceMeters)}")
            if (detail != null) {
                if (detail.startDate.isNotBlank()) {
                    lines.add("기간: ${formatYyyymmdd(detail.startDate)} ~ ${formatYyyymmdd(detail.endDate)}")
                }
                if (detail.playTime.isNotBlank()) lines.add("운영시간: ${detail.playTime}")
                if (detail.place.isNotBlank()) lines.add("장소: ${detail.place}")
                if (detail.useTime.isNotBlank()) lines.add("이용요금: ${detail.useTime}")
            }
            MapPin(
                name = f.name, lat = f.lat, lon = f.lon,
                subtitle = "${formatDistance(f.distanceMeters)} / ${f.addr}",
                detailLines = lines
            )
        }
    }

    private fun formatYyyymmdd(s: String): String =
        if (s.length == 8) "${s.substring(0, 4)}.${s.substring(4, 6)}.${s.substring(6, 8)}" else s

    // 주유소/메뉴 체크박스는 위치 자체는 같은 로컬 DB(ExRestAreaStore)의 같은
    // 최근접 5곳을 쓰지만, 핀 클릭 상세는 각자 다른 EX 실시간 API를 추가로
    // 호출한다(2026-10-25 "주유소는 가격, 메뉴는 대표메뉴" 요청 - ExDetailStore
    // 참고, rest.sce의 EX가격/EX메뉴 체인과 동일 API).
    private suspend fun fetchGasPins(lat: Double, lon: Double): List<MapPin> {
        val serviceKey = AddrIniStore.readRaw(filesDir, "EX")
        val domain = IniParser.parse(File(filesDir, "addr.ini").readText())["EX"]?.get("domain")
            ?: "https://data.ex.co.kr/openapi"
        return ExRestAreaStore.nearest(filesDir, lat, lon, limit = 5).map {
            val distance = formatDistance(haversineMeters(lat, lon, it.lat, it.lon).toInt())
            val price = if (serviceKey != null) {
                try { ExDetailStore.fetchGasPrice(domain, serviceKey, it.name) } catch (_: Throwable) { null }
            } else null
            val lines = mutableListOf("거리: $distance")
            if (price != null) {
                lines.add("${price.routeName} ${price.direction}")
                lines.add("주유소: ${price.oilCompany}")
                // EX API는 gasolinePrice/diselPrice에 "원"을 이미 포함해서 준다
                // ("1,848원" 그대로) - 뒤에 또 붙이면 "원원"이 됨(2026-10-25 실기기
                // 확인 후 수정).
                lines.add("휘발유: ${price.gasolinePrice}")
                lines.add("경유: ${price.diselPrice}")
            } else {
                lines.add("가격 정보가 없습니다")
            }
            MapPin(name = it.name, lat = it.lat, lon = it.lon, subtitle = distance, detailLines = lines)
        }
    }

    private suspend fun fetchMenuPins(lat: Double, lon: Double): List<MapPin> {
        val serviceKey = AddrIniStore.readRaw(filesDir, "EX")
        val domain = IniParser.parse(File(filesDir, "addr.ini").readText())["EX"]?.get("domain")
            ?: "https://data.ex.co.kr/openapi"
        return ExRestAreaStore.nearest(filesDir, lat, lon, limit = 5).map {
            val distance = formatDistance(haversineMeters(lat, lon, it.lat, it.lon).toInt())
            val menuItems = if (serviceKey != null) {
                try { ExDetailStore.fetchMenuItems(domain, serviceKey, it.name) } catch (_: Throwable) { emptyList() }
            } else emptyList()
            val lines = mutableListOf("거리: $distance")
            if (menuItems.isNotEmpty()) {
                menuItems.forEach { m -> lines.add("${m.foodName} - ${m.foodCost}원") }
            } else {
                lines.add("메뉴 정보가 없습니다")
            }
            MapPin(name = it.name, lat = it.lat, lon = it.lon, subtitle = distance, detailLines = lines)
        }
    }

    private suspend fun fetchParkingPins(lat: Double, lon: Double): List<MapPin> {
        val match = resolveParkingRegion(lat, lon) ?: return emptyList()
        val serviceKey = AddrIniStore.readRaw(filesDir, "K_DATA") ?: return emptyList()
        val domain = IniParser.parse(File(filesDir, "addr.ini").readText())["PARKING"]?.get("domain")
            ?: "https://apis.data.go.kr/B553881/Parking_v1"
        val nearest = ParkingNearbyStore.findNearest(
            domain = domain, serviceKey = serviceKey,
            sidoCd = match.parentCode5.take(2), sigunguCd = match.parentCode5,
            userLat = lat, userLon = lon, feeFilter = null
        )
        return nearest.map { p ->
            MapPin(
                name = p.name, lat = p.lat, lon = p.lon,
                subtitle = "${formatDistance(p.distanceMeters)} / ${p.feeName}",
                detailLines = listOf(
                    "주소: ${p.addr}",
                    "거리: ${formatDistance(p.distanceMeters)}",
                    "주차면수: ${p.spaceCount}면",
                    "요금: ${p.feeName}",
                    "관리기관: ${p.agentName}"
                )
            )
        }
    }

    /** 각 fetch*Pins가 공용으로 쓰는 거리 계산(ParkingNearbyStore에도 같은
     *  공식이 있지만 private이라 공유 불가 - 10줄짜리 표준 공식이라 중복 비용이
     *  거의 없음). */
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return r * c
    }

    /**
     * 행사 "내 주변"만 markers 필드를 들고 온다(2026-10-25 "지도로 보기"
     * 추가) - rest.sce엔 JSON 배열을 직접 조립할 수단이 없어, text 필드의
     * "|" 구분 관례를 그대로 재사용해 "이름^위도^경도"를 "|"로 이어붙인
     * 문자열로 보낸다. 값이 없거나(다른 TOUR 명령) "없음"(매 명령 시작 시
     * 초기화값, rest.sce 참고)이면 null - 주유소/메뉴/주차장은 이 필드를
     * 안 쓰고 각자 다른 경로(pendingNearbyMarkers/직접 전달)로 마커를 받는다.
     */
    private fun extractMarkers(json: JSONObject): List<MapPin>? {
        val raw = json.optString("markers")
        if (raw.isBlank() || raw == "없음") return null
        val pins = raw.split("|").mapNotNull { line ->
            val parts = line.split("^")
            if (parts.size != 3) return@mapNotNull null
            val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            val lon = parts[2].toDoubleOrNull() ?: return@mapNotNull null
            MapPin(name = parts[0], lat = lat, lon = lon)
        }
        return pins.ifEmpty { null }
    }
}

// 모든 "내 주변" 기능(행사/주유소/메뉴/주차장)이 공유하는 위치 획득 함수.
// (2026-10-20 "회사로 자리를 이동했는데 GPS 정보는 계속 집"이라는 실사용
// 버그 리포트로 1차 수정했다가, 바로 다음 리포트 - "회사에선 실패, 집에
// 오니 됨" - 로 2차 수정됨.) 1차 수정은 "캐시가 오래됐으면 새 위치를
// 요청하되, 타임아웃되면 그 오래된 캐시로라도 폴백한다"였는데, 바로 이
// 폴백이 범인이었다 - 회사(실내, GPS/네트워크 신호가 약한 환경)에서는
// 새 위치 요청이 타임아웃까지 하나도 못 받는 경우가 실제로 있고, 그러면
// 결국 "오래된 폴백"이 집에서 얻은 캐시를 그대로 돌려줘 똑같은 증상이
// 재현된다(사용자 입장에선 "검색이 안 됨"이나 마찬가지 - 엉뚱한 위치
// 기준으로 나온 결과니까). 이번엔 폴백에도 신선도 상한
// (FRESH_LOCATION_FALLBACK_MAX_AGE_MS)을 둬서, 그 상한보다 오래된
// 캐시는 아예 쓰지 않고 null(실패)을 돌려준다 - "엉뚱한 옛 위치로
// 조용히 성공한 척"하는 것보다 "지금 위치를 못 구했다"고 솔직하게 실패하는
// 쪽이 사용자에게 훨씬 낫다. Play Services 등 새 의존성 없이 프레임워크
// LocationManager만 쓰는 기존 설계 원칙은 그대로 유지했다.
private const val FRESH_LOCATION_MAX_AGE_MS = 3 * 60 * 1000L
private const val FRESH_LOCATION_TIMEOUT_MS = 15_000L
private const val FRESH_LOCATION_FALLBACK_MAX_AGE_MS = 10 * 60 * 1000L

private const val LOCATION_TAG = "phoneFlow/Location"

// internal(private 아님) - MapBrowserScreen.kt(2026-10-25 "지도" 체크박스
// 브라우저 추가)도 같은 GPS 로직을 재사용한다.
internal fun requestFreshLocation(context: Context, onResult: (Location?) -> Unit) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = lm.getProviders(true)
    Log.i(LOCATION_TAG, "요청 시작 - 사용 가능한 provider=$providers")

    fun cachedLocations(): List<Location> = providers.mapNotNull { provider ->
        try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
    }

    val now = System.currentTimeMillis()
    val candidates = cachedLocations()
    Log.i(
        LOCATION_TAG,
        "캐시 확인 - " + candidates.joinToString(", ") {
            "${it.provider}(age=${(now - it.time) / 1000}s, acc=${it.accuracy}m)"
        }.ifEmpty { "(캐시 없음)" }
    )
    val freshCached = candidates
        .filter { now - it.time <= FRESH_LOCATION_MAX_AGE_MS }
        .minByOrNull { it.accuracy }
    if (freshCached != null) {
        Log.i(LOCATION_TAG, "신선한 캐시 사용 - ${freshCached.provider}, age=${(now - freshCached.time) / 1000}s")
        onResult(freshCached)
        return
    }

    // 캐시가 전부 오래됐다 - 실제로 새 위치를 요청한다. 타임아웃 안에 새
    // 위치를 하나도 못 받으면, 그나마 있는 캐시 중 "그래도 너무 오래되진
    // 않은"(FRESH_LOCATION_FALLBACK_MAX_AGE_MS 이내) 것만 폴백으로 쓰고,
    // 그마저 없으면 null(실패)을 돌려준다.
    Log.i(LOCATION_TAG, "캐시가 오래됨 - 새 위치 요청 시작(타임아웃 ${FRESH_LOCATION_TIMEOUT_MS}ms)")
    val handler = Handler(Looper.getMainLooper())
    var finished = false
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (finished) return
            finished = true
            lm.removeUpdates(this)
            handler.removeCallbacksAndMessages(null)
            Log.i(LOCATION_TAG, "새 위치 수신 - ${location.provider}, acc=${location.accuracy}m")
            onResult(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {
            Log.i(LOCATION_TAG, "provider 활성화됨 - $provider")
        }
        override fun onProviderDisabled(provider: String) {
            Log.w(LOCATION_TAG, "provider 비활성화됨 - $provider")
        }
    }
    try {
        for (provider in providers) {
            try {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            } catch (t: Exception) {
                // 일부 기기/제공자 조합에서 요청 자체가 거부될 수 있음 - 다른
                // provider는 계속 시도한다.
                Log.w(LOCATION_TAG, "$provider 요청 거부됨: ${t.message}")
            }
        }
    } catch (_: SecurityException) {
        Log.w(LOCATION_TAG, "위치 권한 없음(SecurityException)")
        onResult(null)
        return
    }
    handler.postDelayed({
        if (!finished) {
            finished = true
            lm.removeUpdates(listener)
            val timeoutNow = System.currentTimeMillis()
            val timeoutCandidates = cachedLocations()
            Log.w(
                LOCATION_TAG,
                "타임아웃 - 폴백 후보: " + timeoutCandidates.joinToString(", ") {
                    "${it.provider}(age=${(timeoutNow - it.time) / 1000}s)"
                }.ifEmpty { "(없음)" }
            )
            val fallback = timeoutCandidates
                .filter { timeoutNow - it.time <= FRESH_LOCATION_FALLBACK_MAX_AGE_MS }
                .minByOrNull { it.accuracy }
            if (fallback == null) {
                Log.w(LOCATION_TAG, "폴백도 없음 - 실패(null) 반환")
            } else {
                Log.i(LOCATION_TAG, "폴백 사용 - ${fallback.provider}, age=${(timeoutNow - fallback.time) / 1000}s")
            }
            onResult(fallback)
        }
    }, FRESH_LOCATION_TIMEOUT_MS)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(
    messages: List<ChatMessage>,
    filesDir: File,
    onSend: (String) -> Unit,
    onNearbyParkingResolved: (Double, Double) -> Unit,
    onNearbyParkingFreeResolved: (Double, Double) -> Unit,
    onNearbyParkingPaidResolved: (Double, Double) -> Unit,
    onNearbyParkingLocationFailed: (String) -> Unit,
    onParkingCheapestResolved: (Double, Double) -> Unit,
    onNearbyMarkersComputed: (String, List<MapPin>) -> Unit,
    onFetchFestivalPins: suspend (Double, Double) -> List<MapPin>,
    onFetchGasPins: suspend (Double, Double) -> List<MapPin>,
    onFetchMenuPins: suspend (Double, Double) -> List<MapPin>,
    onFetchParkingPins: suspend (Double, Double) -> List<MapPin>
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
    // Screen.MAP 하나를 모든 "내 주변" 응답의 "지도로 보기"가 같이 쓴다
    // (2026-10-25 추가) - 어느 메시지의 "지도로 보기"를 눌렀는지만 여기 담아둔다.
    var mapScreenPins by remember { mutableStateOf<List<MapPin>>(emptyList()) }

    // "행사 > 내 주변"(GPS 기반 locationBasedList2, 2026-10-09 추가) - 위치
    // 권한이 이미 있으면 requestFreshLocation으로 위치를 구해 "주변행사
    // 위도@경도 " 명령을 채우고, 없으면 시스템 권한 요청 다이얼로그를 띄운 뒤
    // 승인되면 이어서 같은 동작을 한다. Play Services 등 새 의존성 없이
    // 프레임워크 LocationManager만 쓴다(requestFreshLocation 주석 참고 -
    // 2026-10-20부터 단순 getLastKnownLocation이 아니라 캐시 신선도를
    // 확인하고 필요하면 새로 요청함).
    val context = LocalContext.current
    fun fillNearbyFestivalCommand() {
        requestFreshLocation(context) { best ->
            if (best == null) {
                val insert = "주변행사 "
                input = TextFieldValue(insert, selection = TextRange(insert.length))
            } else {
                val insert = "주변행사 ${best.latitude}@${best.longitude} "
                input = TextFieldValue(insert, selection = TextRange(insert.length))
            }
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
        requestFreshLocation(context) { best ->
            if (best == null) {
                val insert = "주유소 "
                input = TextFieldValue(insert, selection = TextRange(insert.length))
            } else {
                val nearest = ExRestAreaStore.nearest(filesDir, best.latitude, best.longitude, limit = 5)
                val insert = if (nearest.isEmpty()) {
                    "주유소 "
                } else {
                    "주유소 내주변 " + nearest.joinToString("|") { it.name }
                }
                input = TextFieldValue(insert, selection = TextRange(insert.length))
                if (nearest.isNotEmpty()) {
                    onNearbyMarkersComputed(insert, nearest.map { MapPin(name = it.name, lat = it.lat, lon = it.lon) })
                }
            }
        }
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
        requestFreshLocation(context) { best ->
            if (best == null) {
                val insert = "메뉴 "
                input = TextFieldValue(insert, selection = TextRange(insert.length))
            } else {
                val nearest = ExRestAreaStore.nearest(filesDir, best.latitude, best.longitude, limit = 5)
                val insert = if (nearest.isEmpty()) {
                    "메뉴 "
                } else {
                    "메뉴 내주변 " + nearest.joinToString("|") { it.name }
                }
                input = TextFieldValue(insert, selection = TextRange(insert.length))
                if (nearest.isNotEmpty()) {
                    onNearbyMarkersComputed(insert, nearest.map { MapPin(name = it.name, lat = it.lat, lon = it.lon) })
                }
            }
        }
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

    // "주차장" 드로워의 내 주변/무료만/유료만/저렴한 순(2026-10-19 내 주변
    // 추가, 2026-10-24 "전부 GPS 기준으로" 요청으로 나머지 셋도 동일 패턴으로
    // 전환) - 좌표를 구하는 부분까지는 위 세 기능(행사/주유소/휴게소)과
    // 동일하지만, 그 다음이 다르다: 입력창에 명령을 채우는 대신 좌표를
    // 그대로 MainActivity로 넘긴다 - 실제 거리/가격 계산을 위해 지오코딩+
    // API 호출+정렬이 필요해(ParkingNearbyStore 참고) 더 이상 "한 줄 채팅
    // 명령"으로 표현할 수 없기 때문이다. 네 기능 모두 같은 모양이라
    // "어떤 콜백을 부를지"만 다른 공통 함수로 묶었다.
    fun handleParkingLocation(onResolved: (Double, Double) -> Unit, onFailed: () -> Unit) {
        requestFreshLocation(context) { best ->
            if (best == null) onFailed() else onResolved(best.latitude, best.longitude)
        }
    }
    fun fillNearbyParkingCommand() {
        handleParkingLocation(onNearbyParkingResolved) { onNearbyParkingLocationFailed("주차장 내 주변") }
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

    fun fillNearbyParkingFreeCommand() {
        handleParkingLocation(onNearbyParkingFreeResolved) { onNearbyParkingLocationFailed("주차장 무료만 보기 (내 주변)") }
    }
    val locationPermissionLauncherParkingFree = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) fillNearbyParkingFreeCommand() }
    fun requestNearbyParkingFree() {
        val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            fillNearbyParkingFreeCommand()
        } else {
            locationPermissionLauncherParkingFree.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun fillNearbyParkingPaidCommand() {
        handleParkingLocation(onNearbyParkingPaidResolved) { onNearbyParkingLocationFailed("주차장 유료만 보기 (내 주변)") }
    }
    val locationPermissionLauncherParkingPaid = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) fillNearbyParkingPaidCommand() }
    fun requestNearbyParkingPaid() {
        val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            fillNearbyParkingPaidCommand()
        } else {
            locationPermissionLauncherParkingPaid.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun fillParkingCheapestCommand() {
        handleParkingLocation(onParkingCheapestResolved) { onNearbyParkingLocationFailed("주차장 저렴한 순 (내 주변)") }
    }
    val locationPermissionLauncherParkingCheapest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) fillParkingCheapestCommand() }
    fun requestParkingCheapest() {
        val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            fillParkingCheapestCommand()
        } else {
            locationPermissionLauncherParkingCheapest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // "지도에서 드래그가 잘 안 먹힌다, 좌측 슬라이드 메뉴 때문인가?"
        // (2026-10-25 피드백) - 맞다: ModalNavigationDrawer는 기본적으로 화면
        // 전체에서 가장자리 스와이프를 감지해 드로워를 열려고 하는데, 이
        // 제스처 감지가 지도의 팬(드래그) 제스처와 경쟁해 지도 조작이
        // 끊기거나 씹히는 원인이 된다. 드로워는 이미 햄버거 버튼으로 항상
        // 열 수 있으니, 지도 화면에서만 스와이프 제스처를 꺼서 지도가 전체
        // 드래그 영역을 온전히 가져가게 한다.
        gesturesEnabled = screen != Screen.MAP_BROWSER,
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
                    },
                    onNearbyParkingFreeRequested = {
                        requestNearbyParkingFree()
                        screen = Screen.CHAT
                        scope.launch { drawerState.close() }
                    },
                    onNearbyParkingPaidRequested = {
                        requestNearbyParkingPaid()
                        screen = Screen.CHAT
                        scope.launch { drawerState.close() }
                    },
                    onParkingCheapestRequested = {
                        requestParkingCheapest()
                        screen = Screen.CHAT
                        scope.launch { drawerState.close() }
                    },
                    onMapBrowserPicked = {
                        screen = Screen.MAP_BROWSER
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
                                    Screen.MAP -> "지도로 보기"
                                    Screen.MAP_BROWSER -> "지도"
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
                        } else {
                            // "채팅 화면에도 일관성 있게 지도로 가는 아이콘이 있어야
                            // 한다"(2026-10-25 피드백) - 다른 화면들의 ChatEntryButton
                            // (채팅으로 바로 복귀)과 대칭되는, 채팅에서 지도로 바로
                            // 가는 버튼.
                            MapEntryButton(onClick = { screen = Screen.MAP_BROWSER })
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
                        },
                        onShowMap = { pins ->
                            mapScreenPins = pins
                            screen = Screen.MAP
                        }
                    )
                    Screen.MAP -> NearbyMapScreen(pins = mapScreenPins)
                    Screen.MAP_BROWSER -> MapBrowserScreen(
                        onFetchFestival = onFetchFestivalPins,
                        onFetchGas = onFetchGasPins,
                        onFetchMenu = onFetchMenuPins,
                        onFetchParking = onFetchParkingPins
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
    onNearbyParkingRequested: () -> Unit,
    onNearbyParkingFreeRequested: () -> Unit,
    onNearbyParkingPaidRequested: () -> Unit,
    onParkingCheapestRequested: () -> Unit,
    onMapBrowserPicked: () -> Unit
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
                        } else if (group.label == "주차장" && cmd.label == "무료만 보기") {
                            // 2026-10-24 "무료만/유료만/저렴한 순도 GPS 기준으로"
                            // 요청으로 지역 피커 대신 "내 주변"과 같은 GPS 흐름으로
                            // 전환 - 거리순 정렬에 무료 필터만 더한 것.
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onNearbyParkingFreeRequested() }
                                )
                            }
                        } else if (group.label == "주차장" && cmd.label == "유료만 보기") {
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onNearbyParkingPaidRequested() }
                                )
                            }
                        } else if (group.label == "주차장" && cmd.label == "저렴한 순") {
                            // 기본요금 비교엔 정렬이 필요해(DSL엔 정렬 함수가 없음)
                            // "내 주변"과 동일한 GPS 흐름으로 onParkingCheapestRequested를
                            // 바로 호출한다(2026-10-24 지역 피커 방식에서 전환).
                            item(key = "item-${group.label}-${cmd.label}") {
                                Text(
                                    cmd.label,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 32.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                        .clickableCompat { onParkingCheapestRequested() }
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
            "🗺️ 지도",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .clickableCompat { onMapBrowserPicked() }
        )
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

/** ChatEntryButton과 대칭 - 채팅 화면에서 "지도" 브라우저로 바로 가는
 *  버튼(2026-10-25 "일관성 있게 지도 아이콘이 있어야" 요청). */
@Composable
private fun MapEntryButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        color = Color.White
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("🗺️")
        }
    }
}

@Composable
private fun ChatScreen(
    messages: List<ChatMessage>,
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    onSend: (String) -> Unit,
    onShowMap: (List<MapPin>) -> Unit
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
                ChatBubble(msg, onShowMap)
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMessage, onShowMap: (List<MapPin>) -> Unit) {
    val alignment = if (msg.fromUser) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.align(alignment)) {
            Row(verticalAlignment = Alignment.Bottom) {
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
            // "내 주변" 응답(행사/주유소/메뉴/주차장)에만 붙는 버튼(2026-10-25
            // 추가) - msg.markers가 비어있지 않을 때만 보인다. 34dp는
            // 아바타(26dp)+간격(4dp)+여백 정도로 버블 시작 위치에 맞춘 값.
            if (!msg.markers.isNullOrEmpty()) {
                Surface(
                    modifier = Modifier
                        .padding(start = 34.dp, bottom = 4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onShowMap(msg.markers) },
                    color = PhoneFlowBlueLight,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "📍 지도로 보기",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
