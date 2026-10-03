package com.dorafather.phoneflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.dorafather.phoneflow.net.FlowMessageRouter
import com.dorafather.phoneflow.net.WatchlistStore
import com.notebookflow.engine.FlowBridge
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

private const val TAG = "phoneFlow/JNI"

data class ChatMessage(val text: String, val fromUser: Boolean)

private enum class Screen { CHAT, SETTINGS }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prevHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            Log.e(TAG, "[DIAG] 처리되지 않은 예외 (thread=${thread.name})", e)
            prevHandler?.uncaughtException(thread, e)
        }

        FlowMessageRouter.setFinalMessageListener { json ->
            // ACTION() 콜백은 엔진 전용 네이티브 스레드(또는 AndroidHttpClient의
            // 워커 스레드)에서 올 수 있으므로, Compose 상태 변경은 반드시
            // runOnUiThread로 메인 스레드에 올린다.
            runOnUiThread {
                messages.add(ChatMessage(text = extractDisplayText(json), fromUser = false))
            }
        }

        try {
            copyAssetScenarioFiles()
            FlowBridge.nativeSetCallback(FlowMessageRouter)
            FlowBridge.nativeInit(filesDir.absolutePath)
            Log.i(TAG, "FlowBridge.nativeInit() 호출 성공 (baseDir=${filesDir.absolutePath})")
        } catch (t: Throwable) {
            Log.e(TAG, "FlowBridge native 호출 실패", t)
            messages.add(ChatMessage(text = "엔진 초기화 실패: ${t.message}", fromUser = false))
        }

        val commandGroups = loadCommandGroups(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        messages = messages,
                        commandGroups = commandGroups,
                        filesDir = filesDir,
                        onSend = ::sendChatInput
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
        for (name in listOf("addr.ini", "rest.sce")) {
            val dest = File(filesDir, name)
            if (dest.exists()) continue
            assets.open(name).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        Log.i(TAG, "시나리오(addr.ini/rest.sce) 준비 완료 -> ${filesDir.absolutePath}")
    }

    private fun sendChatInput(text: String) {
        messages.add(ChatMessage(text = text, fromUser = true))
        val event = JSONObject().apply {
            put("이벤트명", "채팅입력")
            put("text", text)
        }
        FlowBridge.nativePushEvent(event.toString())
    }

    private fun extractDisplayText(json: JSONObject): String {
        // 핑 테스트 결과("봇응답" 이벤트, text 필드)와 그 외 메시지 모두
        // 안전하게 사람이 읽을 수 있는 형태로 보여준다.
        if (json.has("text")) return json.optString("text")
        return json.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(
    messages: List<ChatMessage>,
    commandGroups: List<CommandGroup>,
    filesDir: File,
    onSend: (String) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.CHAT) }
    // TextFieldValue로 들고 있어야 서랍에서 텍스트를 넣을 때 커서를 항상
    // 맨 끝에 둘 수 있다(업무지침 "커서를 맨 끝에 두고 사용자가 인자를
    // 채워 전송" 요구사항) - 일반 String 상태로는 커서 위치를 보장할 수 없다.
    var input by remember { mutableStateOf(TextFieldValue("")) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                CommandDrawerContent(
                    groups = commandGroups,
                    filesDir = filesDir,
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
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (screen == Screen.SETTINGS) "설정" else "phoneFlow") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "메뉴")
                        }
                    }
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
                    Screen.SETTINGS -> SettingsScreen(filesDir = filesDir)
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
private val LEAF_LABEL_TO_SUBTREE = mapOf(
    "기상청" to "지역 조회",
    "미세먼지" to "지역 조회",
    "실거래가" to "지역 조회",
    "주식" to "관심종목 조회",
)
private val ITEM_QUERY_TEMPLATES: Map<String, (String) -> String> = mapOf(
    "기상청" to { region -> "기상청 날씨 $region" },
    "미세먼지" to { region -> "미세먼지 $region" },
    "실거래가" to { region -> "실거래가 $region " }, // 계약년월은 사용자가 이어서 입력
    "주식" to { name -> "주식 시세 $name" }
)

@Composable
private fun CommandDrawerContent(
    groups: List<CommandGroup>,
    filesDir: File,
    onCommandPicked: (String) -> Unit,
    onSettingsPicked: () -> Unit
) {
    val expanded = remember { mutableStateListOf<String>() }
    val expandedQueries = remember { mutableStateListOf<String>() }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)) {
        Text(
            "phoneFlow",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            groups.forEach { group ->
                item(key = "group-${group.label}") {
                    val isOpen = expanded.contains(group.label)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (if (isOpen) "▾ " else "▸ ") + group.label,
                            style = MaterialTheme.typography.titleMedium,
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
                        val itemTemplate = ITEM_QUERY_TEMPLATES[group.label]
                        if (cmd.label == LEAF_LABEL_TO_SUBTREE[group.label] && itemTemplate != null) {
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
                                    names.forEach { name ->
                                        item(key = "item-$qKey-$name") {
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
    }
}

private fun Modifier.clickableCompat(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)

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
                    placeholder = { Text("메시지를 입력하세요 (예: 핑)") }
                )
                Button(onClick = {
                    val text = input.text.trim()
                    if (text.isNotEmpty()) {
                        onSend(text)
                    }
                }) {
                    Text("전송")
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
        Surface(
            modifier = Modifier
                .align(alignment)
                .padding(4.dp),
            color = if (msg.fromUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = msg.text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
