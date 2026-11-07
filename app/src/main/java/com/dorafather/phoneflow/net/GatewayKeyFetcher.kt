package com.dorafather.phoneflow.net

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * EX(data.ex.co.kr)는 Cloudflare Workers 게이트웨이(phoneflow-gw) IP 대역을
 * 네트워크 단에서 차단해(522 타임아웃 실측 확인, 2026-11-07) 다른 17개
 * 섹션처럼 요청 자체를 게이트웨이로 대신 보내는 방식을 못 쓴다. 대신 "실키
 * 다운로드"만 게이트웨이를 거치고, 실제 EX 호출은 여전히 기기 자신의
 * 통신사/와이파이 IP로 직접 나가게 한다 - EX 서버 입장에서는 차단 대상인
 * Cloudflare IP가 아니라 평범한 국내 모바일 IP로 보이므로 그대로 통과한다.
 *
 * 앱 기동 순서를 바꿀 필요는 없다 - 엔진의 IniFileReader가 addr.ini 변경을
 * 1초 폴링으로 감지해 즉시 반영하므로(AddrIniStore.kt 문서 참고), 이 다운로드는
 * nativeInit() 전후 상관없이 그냥 백그라운드에서 한 번 실행해 덮어쓰기만
 * 하면 된다. 실패해도 기존 파일 값(이전에 성공한 적이 있다면 그 실키,
 * 아니면 플레이스홀더)을 그대로 두므로 안전하게 무시한다.
 */
object GatewayKeyFetcher {

    private const val TAG = "phoneFlow/GatewayKey"
    private const val KEYS_URL = "https://phoneflow-gw.shiury.workers.dev/keys"
    private const val GATEWAY_TOKEN = "49056bec570d47a096bc2deffedb687b"
    private val executor = Executors.newSingleThreadExecutor()

    fun fetchAndApplyExKey(filesDir: File) {
        executor.execute {
            try {
                val url = URL("$KEYS_URL?token=$GATEWAY_TOKEN")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5_000
                    readTimeout = 5_000
                }
                val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                conn.disconnect()
                val exKey = JSONObject(body).optString("KEY_EX")
                if (exKey.isNotBlank()) {
                    val saved = AddrIniStore.save(filesDir, "EX", exKey)
                    Log.i(TAG, "EX 키 다운로드 및 저장 완료 (saved=$saved)")
                } else {
                    Log.w(TAG, "EX 키 응답이 비어있음 - 기존 값 유지")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "EX 키 다운로드 실패 - 기존 값 유지", t)
            }
        }
    }
}
