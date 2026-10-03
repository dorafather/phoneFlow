package com.dorafather.phoneflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dorafather.phoneflow.net.FlowMessageRouter
import com.notebookflow.engine.FlowBridge
import org.json.JSONObject
import java.io.File

private const val TAG = "phoneFlow/JNI"

data class ChatMessage(val text: String, val fromUser: Boolean)

/**
 * Jetpack Compose 기반 채팅 UI. 기존 JNI 초기화(FlowBridge.nativeInit/
 * nativeSetCallback)는 그대로 재사용하고,
 * ACTION() 콜백 처리만 FlowMessageRouter(REST 통신 모듈의 진입점)로 옮겼다 -
 * MainActivity 자신은 더 이상 FlowCallback을 구현하지 않는다.
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

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreen(messages = messages, onSend = ::sendChatInput)
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
     * 명령(관심종목 추가 등)이 함수.설정저장(SETINI)으로 실제로 써넣는
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

@androidx.compose.runtime.Composable
private fun ChatScreen(messages: List<ChatMessage>, onSend: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
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
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    placeholder = { Text("메시지를 입력하세요 (예: 핑)") }
                )
                Button(onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        onSend(text)
                        input = ""
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

@androidx.compose.runtime.Composable
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
