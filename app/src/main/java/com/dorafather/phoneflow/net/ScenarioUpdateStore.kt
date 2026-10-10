package com.dorafather.phoneflow.net

import java.io.File

private const val RAW_BASE = "https://raw.githubusercontent.com/dorafather/phoneFlow/main/app/src/main/assets"

/**
 * "시나리오 설정" 화면의 수동 "업데이트" 버튼 전용 - GitHub main 브랜치의
 * addr.ini/rest.sce/commands.json 원문을 받아 기기에 적용한다(2026-10-10
 * dorafather 요청). 일일 자동 체크는 이번 범위 밖("시나리오 타이머로 하자,
 * 절전모드 때문에 일단 보류"로 범위 축소).
 *
 * addr.ini는 사용자가 직접 써넣은 값(서비스키/관심종목·관심지역)이 있어
 * GitHub 원문으로 그냥 덮어쓰면 전부 사라진다 - 그래서 GitHub 원문 텍스트를
 * 그대로 베이스로 삼아(주석/포맷 보존) 사용자 소유 라인만 로컬 값으로
 * 치환하는 "수술적 병합"을 한다(AddrIniStore.save()가 service_key 한 줄만
 * 정규식으로 바꾸는 것과 같은 원리를 섹션 단위로 일반화한 것).
 *
 * rest.sce는 사용자 데이터가 없어 그대로 덮어쓴다 - 다만 엔진(RUNFLOW)이
 * 부팅 시 한 번만 읽고 핫리로드를 지원하지 않아(flowjni.cpp nativeInit이
 * 재호출을 무시함, 실측 확인), 실제로 바뀐 내용을 쓰려면 앱 프로세스
 * 재시작이 필요하다 - 그래서 호출자(ScenarioViewerScreen)가 restartNeeded로
 * 재시작 여부를 사용자에게 물어볼 수 있게 한다.
 *
 * commands.json도 사용자 데이터가 없어 그대로 덮어쓰지만, 이건 순수 Kotlin
 * 쪽 데이터(CommandTree.loadAvailableCommandGroups)라 엔진 재시작 없이 드로워를
 * 다음에 열 때 바로 반영된다 - restartNeeded 계산에는 넣지 않는다(2026-10-10
 * dorafather가 "TOUR 지웠는데 메뉴는 그대로네?" 지적 - 예전엔 commands.json이
 * APK assets에서만 읽혀 애초에 갱신 대상이 아니었다).
 */
object ScenarioUpdateStore {

    data class UpdateResult(
        val ok: Boolean,
        val restartNeeded: Boolean,
        val error: String? = null
    )

    private val PRESERVE_SECTIONS_SUFFIX = "_WATCHLIST"

    fun checkAndApply(filesDir: File, onResult: (UpdateResult) -> Unit) {
        fetch("addr.ini") { addrResult ->
            fetch("rest.sce") { sceResult ->
                fetch("commands.json") { cmdResult ->
                    val failed = listOf(addrResult, sceResult, cmdResult).firstOrNull { !it.ok }
                    if (failed != null) {
                        onResult(UpdateResult(ok = false, restartNeeded = false, error = "다운로드 실패 (status=${failed.status})"))
                    } else {
                        onResult(applyDownloaded(filesDir, addrResult.body, sceResult.body, cmdResult.body))
                    }
                }
            }
        }
    }

    private fun fetch(fileName: String, onResult: (AndroidHttpClient.HttpResult) -> Unit) {
        AndroidHttpClient.request("GET", "$RAW_BASE/$fileName", emptyMap(), null, onResult)
    }

    private fun applyDownloaded(filesDir: File, remoteAddr: String, remoteSce: String, remoteCmd: String): UpdateResult {
        return try {
            val addrFile = File(filesDir, "addr.ini")
            val sceFile = File(filesDir, "rest.sce")
            val cmdFile = File(filesDir, "commands.json")

            // GitHub 원문(raw.githubusercontent.com)은 git 블롭 그대로라 LF뿐인데,
            // 이 저장소는 core.autocrlf=true라 기기에 번들된 복사본(앱 설치 시
            // assets/에서 최초 1회 복사됨)은 전부 CRLF다 - 정규화 없이 그대로
            // 비교/저장하면 내용이 전혀 안 바뀌었어도 매번 "달라짐"으로 오판하고
            // 매번 재시작을 유도하게 된다(실기기로 직접 재현한 버그 - 2026-10-10).
            // 엔진이 CRLF로 계속 잘 동작해온 기존 상태를 그대로 유지하기 위해
            // LF만 CRLF로 맞춰준다(엔진 쪽 LF 단독 지원 여부는 검증 범위 밖).
            val normalizedAddr = toCrlf(remoteAddr)
            val normalizedSce = toCrlf(remoteSce)

            val mergedAddr = mergeAddrIni(remoteText = normalizedAddr, localText = addrFile.readText())
            addrFile.writeText(mergedAddr)

            val localSce = if (sceFile.exists()) sceFile.readText() else ""
            val sceChanged = localSce != normalizedSce
            if (sceChanged) sceFile.writeText(normalizedSce)

            // commands.json은 JSON이라 파싱에 줄바꿈 종류가 영향을 주지 않고,
            // 드로워가 매번 새로 읽어 restartNeeded 판단도 필요 없어 그냥 덮어쓴다.
            cmdFile.writeText(toCrlf(remoteCmd))

            UpdateResult(ok = true, restartNeeded = sceChanged)
        } catch (t: Throwable) {
            UpdateResult(ok = false, restartNeeded = false, error = t.message ?: t.toString())
        }
    }

    private fun toCrlf(text: String): String = text.replace("\r\n", "\n").replace("\n", "\r\n")

    /**
     * GitHub 원문(주석/포맷 포함)을 그대로 베이스로 두고, 사용자 소유 라인
     * (K_DATA.service_key, *_WATCHLIST 섹션의 모든 키)만 로컬 값으로
     * 교체한다. 로컬에만 있고 원문엔 없는 섹션/키는 건드리지 않는다(원문
     * 라인 자체가 없어 치환할 자리가 없음 - 서버가 섹션을 통째로 지우는
     * 건 있을 수 없다고 보고 이번 범위에서는 대응하지 않음).
     */
    fun mergeAddrIni(remoteText: String, localText: String): String {
        val local = IniParser.parse(localText)
        val lines = remoteText.lines().toMutableList()
        var currentSection: String? = null

        for (i in lines.indices) {
            val raw = lines[i]
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(";") || trimmed.startsWith("#")) continue
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                currentSection = trimmed.substring(1, trimmed.length - 1)
                continue
            }
            val section = currentSection ?: continue
            val idx = trimmed.indexOf('=')
            if (idx <= 0) continue
            val key = trimmed.substring(0, idx).trim()
            val preserve = (section == "K_DATA" && key == "service_key") || section.endsWith(PRESERVE_SECTIONS_SUFFIX)
            if (!preserve) continue

            val localValue = local[section]?.get(key) ?: continue
            val indent = raw.substring(0, raw.length - raw.trimStart().length)
            lines[i] = "$indent$key=$localValue"
        }
        // remoteText가 이미 toCrlf()로 정규화된 뒤 들어온다는 전제 - String.lines()는
        // 줄바꿈 문자를 버리고 쪼개므로, 다시 합칠 때도 같은 CRLF로 맞춰줘야 한다
        // (그냥 "\n"으로 합치면 여기서 다시 LF로 되돌아가버림).
        return lines.joinToString("\r\n")
    }
}
