package com.dorafather.phoneflow.net

import android.util.Log
import com.notebookflow.engine.FlowBridge
import com.notebookflow.engine.FlowCallback
import org.json.JSONObject
import java.net.URLEncoder

/**
 * JniApp::ACTION()(C++, flowjni.cpp가 아니라 그 안에서 호출되는 JniApp.cpp)이
 * 콜백으로 넘기는 모든 RestMsg JSON의 첫 관문. Windows의 Main.cpp::App::ACTION()과
 * 동격인 "Android 호스트 레이어" 로직이지만, libUtil 수정 없이 이미 완전히
 * 가상함수(pure virtual)로 추상화되어 있던 ACTION() 콜백 메커니즘을 그대로
 * 이용해 전부 Kotlin 쪽에 구현했다(업무지침서가 C안으로 명시한 두 갈래 중
 * "libUtil 안 건드림" 조건을 만족하는 더 단순한 쪽 - 새 C++ host 클래스 없이도
 * 가능함을 ACTION()이 이미 가상함수라는 사실로 확인했다).
 *
 * 분기 기준은 Windows와 동일(2부 2.11절): 전송메시지.메소드(한글 DSL 필드,
 * BASICPARSER.h의 DEF_DSL_K_METHOD_kor="메소드") 길이가 0보다 크면 "요청 모드"
 * (실제 외부 HTTP 호출 필요) - 이 Kotlin 레이어가 주소 오브젝트로 URL을 조립해
 * [AndroidHttpClient]로 진짜 HTTPS 요청을 보내고, 응답을 다시
 * FlowBridge.nativePushEvent()로 엔진에 돌려준다(Flow::PUT()과 동일한 재투입
 * 경로). 메소드가 비어있으면(echo/최종 메시지) 엔진이 더 할 일이 없다고 판단한
 * 것이므로 UI 콜백으로 그대로 넘긴다.
 */
object FlowMessageRouter : FlowCallback {

    private const val TAG = "phoneFlow/Router"

    // ⚠️ 최상위 키만 영문으로 온다 - 엔진(libUtil)이 ACTION() 콜백을 부르기
    // "전"에 고정된 화이트리스트(주소->ADDR, 메소드->METHOD 등)로 최상위 키를
    // 영문으로 바꿔둔다(실기기 실측으로 확인 - 한글 키로 읽으면 값이 항상
    // 비어 있어 전송 자체가 조용히 스킵된다). 중첩 필드(도메인/경로/파라미터/
    // 헤더)는 이 화이트리스트에 없어 한글 그대로 유지된다.
    private const val K_METHOD = "METHOD"
    private const val K_ADDR = "ADDR"
    private const val K_DOMAIN = "도메인"
    private const val K_PATH = "경로"
    private const val K_PARAMS = "파라미터"
    private const val K_HEADERS = "헤더"
    private const val K_RSP_CODE = "응답코드"

    // 반대 방향(Kotlin -> 엔진으로 nativePushEvent 재투입)은 번역이 적용되지
    // 않는다 - 그 변환은 엔진이 "밖으로" 내보낼 때만 거치는 편도 처리이고,
    // STATE의 도메인 매칭(NAMESPACE.수신메시지.주소.도메인)은 한글 키를 그대로
    // 찾는다. 그래서 응답 메시지를 만들 때는 반드시 한글 키로 되돌려 넣어야 한다.
    private const val K_ADDR_FOR_REINJECT = "주소"
    private const val K_METHOD_FOR_REINJECT = "메소드"

    /** Compose UI가 등록하는 "최종 메시지(= 엔진이 더 전송할 게 없는 메시지)" 리스너. */
    fun interface FinalMessageListener {
        fun onFinalMessage(json: JSONObject)
    }

    @Volatile
    private var uiListener: FinalMessageListener? = null

    fun setFinalMessageListener(listener: FinalMessageListener?) {
        uiListener = listener
    }

    override fun onFlowEvent(json: String) {
        Log.i(TAG, "onFlowEvent: ${AndroidHttpClient.maskSensitiveForLog(json)}")
        try {
            val obj = JSONObject(json)
            val method = obj.optString(K_METHOD, "")
            val addr = obj.optJSONObject(K_ADDR)

            if (method.isNotBlank() && addr != null) {
                performOutboundRequest(method, addr, obj)
            } else {
                // 메소드가 없다 = Windows 쪽 "응답(echo)" 모드와 같은 의미: 엔진이
                // 이 메시지를 더 이상 외부로 보낼 필요가 없다고 판단한 최종 결과다.
                uiListener?.onFinalMessage(obj)
            }
        } catch (t: Throwable) {
            // onFlowEvent는 네이티브 JNI 콜백(JniApp::ACTION)에서 직접 호출된다 -
            // 여기서 예외가 새어나가면 JNIEnv에 pending exception으로만 남고
            // 아무 로그도 없이 조용히 증발한다(실측 확인). 반드시 이 바깥
            // 경계에서 잡아 로그로 남긴다.
            Log.e(TAG, "onFlowEvent 처리 중 예외 - 원문 그대로 UI에 전달", t)
            uiListener?.onFinalMessage(JSONObject().put("text", json))
        }
    }

    /**
     * libUtil/parser/BASICPARSER.cpp의 deserialPath()와 완전히 동일한 치환
     * ("^^^" -> "/")을 그대로 미러링한다. 엔진의 ActionParser가 전송::/타이머::
     * 본문의 모든 대입문 값에 serialPath()("/" -> "^^^", URL 경로의 "/"를
     * 다른 구분자와 혼동하지 않기 위한 내부 인코딩)를 파싱 시점에 적용해두기
     * 때문에, 여기서 복원하지 않으면 경로/파라미터/헤더/바디 값 안의 모든
     * "/"가 "^^^"로 깨진 채로 외부에 나간다(실기기 실측으로 발견).
     */
    private fun deserialPath(s: String): String = s.replace("^^^", "/")

    private fun performOutboundRequest(method: String, addr: JSONObject, original: JSONObject) {
        val domain = deserialPath(addr.optString(K_DOMAIN, ""))
        val path = deserialPath(addr.optString(K_PATH, ""))
        val url = buildUrl(domain, path, addr.optJSONArray(K_PARAMS))

        val headers = mutableMapOf<String, String>()
        original.optJSONArray(K_HEADERS)?.let { arr ->
            for (i in 0 until arr.length()) {
                val h = arr.optJSONObject(i) ?: continue
                val key = h.optString("key")
                val value = h.optString("val")
                if (key.isNotBlank()) headers[deserialPath(key)] = deserialPath(value)
            }
        }

        // 라우팅 메타(메소드/주소/헤더)를 뺀 나머지를 바디로 쓴다(POST 등) -
        // 통신 계층은 들어온 라우팅 메타를 소비하고 외부로 나가는 바디에서는
        // 제거한다는 원칙을 따름.
        val bodyObj = JSONObject(original.toString())
        bodyObj.remove(K_METHOD)
        bodyObj.remove(K_ADDR)
        bodyObj.remove(K_HEADERS)
        deserializeStringsInPlace(bodyObj)
        val body = if (method.uppercase() == "GET") null else bodyObj.toString()

        if (url == null) {
            Log.e(TAG, "주소.도메인이 비어있어 요청을 보낼 수 없음: $original")
            return
        }

        AndroidHttpClient.request(method, url, headers, body) { result ->
            val rsp = JSONObject()
            // httpbin 등 응답 바디가 JSON이면 필드 그대로 병합(Windows의
            // rspMsg.PARSE(_resp.c_str())와 동일한 의도) - JSON이 아니면 무시하고
            // 라우팅 메타만 돌려준다(원본 코드도 비-JSON 응답을 안전하게
            // 무시하는 전례가 있음, Slack "ok" 평문 응답 등).
            try {
                val parsedBody = JSONObject(result.body)
                parsedBody.keys().forEach { k -> rsp.put(k, parsedBody.get(k)) }
            } catch (_: Exception) {
                if (result.body.isNotEmpty()) rsp.put("raw_body", result.body)
            }

            rsp.put(K_RSP_CODE, result.status)
            rsp.put(K_METHOD_FOR_REINJECT, method)
            rsp.put(K_ADDR_FOR_REINJECT, addr) // 응답 라우팅(STATE의 NAMESPACE.수신메시지.주소.도메인 매칭)을 위해 원본 주소를 한글 키로 echo
            if (result.error != null) rsp.put("error", result.error)

            Log.i(TAG, "엔진으로 재투입: ${AndroidHttpClient.maskSensitiveForLog(rsp.toString())}")
            FlowBridge.nativePushEvent(rsp.toString())
        }
    }

    private fun buildUrl(domain: String, path: String, params: org.json.JSONArray?): String? {
        if (domain.isBlank()) return null
        val sb = StringBuilder(domain)
        if (path.isNotBlank()) sb.append(path)
        if (params != null && params.length() > 0) {
            sb.append('?')
            for (i in 0 until params.length()) {
                val p = params.optJSONObject(i) ?: continue
                val key = deserialPath(p.optString("key"))
                val value = deserialPath(p.optString("val"))
                if (key.isBlank()) continue
                if (i > 0) sb.append('&')
                sb.append(URLEncoder.encode(key, "UTF-8"))
                sb.append('=')
                sb.append(URLEncoder.encode(value, "UTF-8"))
            }
        }
        return sb.toString()
    }

    /** JSONObject/JSONArray를 재귀로 훑으며 모든 문자열 값에 [deserialPath]를 적용한다. */
    private fun deserializeStringsInPlace(value: Any?) {
        when (value) {
            is JSONObject -> {
                val keys = value.keys().asSequence().toList()
                for (k in keys) {
                    when (val v = value.get(k)) {
                        is String -> value.put(k, deserialPath(v))
                        is JSONObject, is org.json.JSONArray -> deserializeStringsInPlace(v)
                        else -> {}
                    }
                }
            }
            is org.json.JSONArray -> {
                for (i in 0 until value.length()) {
                    when (val v = value.get(i)) {
                        is String -> value.put(i, deserialPath(v))
                        is JSONObject, is org.json.JSONArray -> deserializeStringsInPlace(v)
                        else -> {}
                    }
                }
            }
        }
    }
}
