package com.dorafather.phoneflow.net

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 채팅 화면의 메시지 기록을 filesDir/chat_history.json에 영구 저장한다
 * (2026-10-18 "챗봇 내용이 다 삭제되는데 초기 단계에서는 100개까지는
 * 저장하고 있어야 될듯" 피드백 - 그 전까지는 MainActivity의 messages가
 * 순수 메모리 상태(mutableStateListOf)라 프로세스가 재시작될 때마다
 * (디버그/릴리스 재설치는 물론 일반적인 OS 강제종료 후 재실행에도)
 * 대화 내용이 전부 사라졌었다). 최대 [MAX_MESSAGES]개만 보관하며,
 * 그보다 많아지면 가장 오래된 것부터 버린다 - 채팅 DB가 아니라 "최근
 * 대화 복원"이 목적이므로 단순 JSON 배열 파일로 충분하다.
 */
object ChatHistoryStore {
    const val MAX_MESSAGES = 100

    private fun historyFile(filesDir: File) = File(filesDir, "chat_history.json")

    data class StoredMessage(val text: String, val fromUser: Boolean)

    fun load(filesDir: File): List<StoredMessage> {
        val f = historyFile(filesDir)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                StoredMessage(obj.getString("text"), obj.getBoolean("u"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(filesDir: File, messages: List<StoredMessage>) {
        val trimmed = if (messages.size > MAX_MESSAGES) messages.takeLast(MAX_MESSAGES) else messages
        val arr = JSONArray()
        trimmed.forEach { msg ->
            arr.put(JSONObject().apply {
                put("text", msg.text)
                put("u", msg.fromUser)
            })
        }
        try {
            historyFile(filesDir).writeText(arr.toString())
        } catch (_: Exception) {
            // 저장 실패해도 채팅 자체는 계속 동작해야 하므로 조용히 무시한다
            // (디스크 꽉 참 등 - 다음 메시지에서 다시 시도됨).
        }
    }
}
