package com.dorafather.phoneflow.net

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * phoneFlow의 REST 통신 모듈(Android 호스트 레이어, app/src/main/cpp/의 libUtil과는
 * 완전히 분리된 Kotlin 코드). cpp-httplib+OpenSSL 크로스빌드나 libcurl 포팅 대신,
 * Android 플랫폼이 이미 완전히 검증/유지보수하는 TLS 스택(HttpURLConnection ->
 * OS 신뢰 저장소/인증서 체인)을 그대로 쓰기로 했다 - OpenSSL을 직접 크로스빌드해
 * TLS 버전/인증서 체인을 떠안는 유지보수 비용을 피하기 위함.
 *
 * libUtil(엔진 라이브러리) 코드는 전혀 건드리지 않는다 - 통신은 100% 이 파일과
 * [FlowMessageRouter]에서만 일어난다.
 */
object AndroidHttpClient {

    private const val TAG = "phoneFlow/Http"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    // 네트워크 I/O는 반드시 메인 스레드 밖에서 실행해야 한다(Android
    // NetworkOnMainThreadException) - 엔진 전용 네이티브 스레드(RUNFLOW)와는
    // 별개의 작은 고정 풀. Windows OutboundClient의 "워커 스레드 2개 고정"
    // 설계(notebookflow_outbound_client_thread_starvation 메모리 참고)와
    // 비슷한 이유로 무제한 스레드 생성은 피하되, 이번 스켈레톤 범위에서는
    // 4개로 충분하다고 보고 단순하게 고정했다.
    private val executor = Executors.newFixedThreadPool(4)

    data class HttpResult(
        val ok: Boolean,
        val status: Int,
        val body: String,
        val error: String? = null
    )

    /**
     * 실제 HTTP(S) 요청 1건을 비동기로 수행한다. 결과는 반드시 호출한
     * 스레드가 아닌 내부 워커 스레드에서 [callback]으로 전달된다 - 호출자가
     * 메인 스레드 여부를 신경 쓰지 않도록 [FlowMessageRouter] 쪽에서
     * FlowBridge.nativePushEvent()를 아무 스레드에서나 호출해도 안전하다는
     * 전제(엔진 쪽 QTHREAD 큐가 스레드 안전하다는 기존 설계)를 그대로 따른다.
     */
    fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
        callback: (HttpResult) -> Unit
    ) {
        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val u = URL(url)
                conn = (u.openConnection() as HttpURLConnection).apply {
                    requestMethod = method.ifBlank { "GET" }
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                }
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

                if (!body.isNullOrEmpty() && method.uppercase() != "GET") {
                    conn.doOutput = true
                    if (conn.getRequestProperty("Content-Type") == null) {
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    }
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
                }

                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.let {
                    BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
                } ?: ""

                Log.i(TAG, "요청 완료: $method $url -> status=$status bodyLen=${text.length}")
                callback(HttpResult(ok = status in 200..299, status = status, body = text))
            } catch (t: Throwable) {
                Log.e(TAG, "요청 실패: $method $url", t)
                callback(HttpResult(ok = false, status = -1, body = "", error = t.message ?: t.toString()))
            } finally {
                conn?.disconnect()
            }
        }
    }
}
