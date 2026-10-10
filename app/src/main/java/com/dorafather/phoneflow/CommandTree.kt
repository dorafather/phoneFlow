package com.dorafather.phoneflow

import com.dorafather.phoneflow.net.IniParser
import org.json.JSONObject
import java.io.File

/**
 * 명령어 서랍(Navigation Drawer)에 그릴 트리 데이터. 하드코딩하지 않고
 * filesDir/commands.json에서 읽는다 - NotebookFlow의 help.json이 평평한
 * 목록이라 이번 phoneFlow 전용으로 계층 구조(group > item)를 새로 설계했다
 * (업무지침_phoneFlow_UI개선_명령어서랍_설정화면.md 2절 판단 근거).
 *
 * 2026-10-10 - addr.ini/rest.sce처럼 filesDir에 복사한 사본을 읽도록 바꿨다
 * (예전엔 APK assets에서 매번 직접 읽어 "업데이트" 버튼의 GitHub 동기화
 * 대상이 될 수조차 없었음 - dorafather가 TOUR 섹션 삭제 테스트 중 "메뉴는
 * 그대로네?"라고 지적해서 발견한 틈).
 *
 * 여기 적힌 각 item.insert 문자열은 phoneFlow의 실제 rest.sce
 * (처리::FLOW.채팅명령분기 이하)에 구현된 명령과 직접 대조해서 작성했다 -
 * 설계 문서가 아니라 소스 기준.
 *
 * requiresSection: 이 그룹이 동작하려면 addr.ini에 있어야 하는 섹션 이름
 * (예: "행사" 그룹 -> "TOUR"). addr.ini는 "업데이트" 버튼으로 commands.json
 * 보다 먼저/나중에 따로 갱신될 수 있어, 메뉴와 실제 설정이 어긋나는 틈을
 * 막는 안전망이다 - null이면 항상 표시(주식/기상청 등 원래부터 있던
 * 그룹은 연결할 섹션 이름이 commands.json 설계 당시 없었어 비워둠).
 */
data class CommandItem(val label: String, val insert: String)
data class CommandGroup(val label: String, val items: List<CommandItem>, val requiresSection: String? = null)

private fun loadCommandGroups(filesDir: File): List<CommandGroup> {
    val f = File(filesDir, "commands.json")
    if (!f.exists()) return emptyList()
    val root = JSONObject(f.readText(Charsets.UTF_8))
    val groups = root.getJSONArray("groups")
    return (0 until groups.length()).map { gi ->
        val g = groups.getJSONObject(gi)
        val items = g.getJSONArray("items")
        CommandGroup(
            label = g.getString("label"),
            items = (0 until items.length()).map { ii ->
                val it = items.getJSONObject(ii)
                CommandItem(label = it.getString("label"), insert = it.getString("insert"))
            },
            requiresSection = g.optString("requires_section", "").ifBlank { null }
        )
    }
}

/** 드로워가 실제로 그릴 목록 - requiresSection이 있는데 현재 addr.ini에 그
 *  섹션이 없는 그룹은 뺀다. */
fun loadAvailableCommandGroups(filesDir: File): List<CommandGroup> {
    val addrFile = File(filesDir, "addr.ini")
    val sections = if (addrFile.exists()) IniParser.parse(addrFile.readText()).keys else emptySet()
    return loadCommandGroups(filesDir).filter { it.requiresSection == null || it.requiresSection in sections }
}
