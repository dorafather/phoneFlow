package com.dorafather.phoneflow

import android.content.Context
import org.json.JSONObject

/**
 * 명령어 서랍(Navigation Drawer)에 그릴 트리 데이터. 하드코딩하지 않고
 * assets/commands.json에서 읽는다 - NotebookFlow의 help.json이 평평한
 * 목록이라 이번 phoneFlow 전용으로 계층 구조(group > item)를 새로 설계했다
 * (업무지침_phoneFlow_UI개선_명령어서랍_설정화면.md 2절 판단 근거).
 *
 * 여기 적힌 각 item.insert 문자열은 phoneFlow의 실제 rest.sce
 * (처리::FLOW.채팅명령분기 이하)에 구현된 명령과 직접 대조해서 작성했다 -
 * 설계 문서가 아니라 소스 기준.
 */
data class CommandItem(val label: String, val insert: String)
data class CommandGroup(val label: String, val items: List<CommandItem>)

fun loadCommandGroups(context: Context): List<CommandGroup> {
    val text = context.assets.open("commands.json").use { it.readBytes().toString(Charsets.UTF_8) }
    val root = JSONObject(text)
    val groups = root.getJSONArray("groups")
    return (0 until groups.length()).map { gi ->
        val g = groups.getJSONObject(gi)
        val items = g.getJSONArray("items")
        CommandGroup(
            label = g.getString("label"),
            items = (0 until items.length()).map { ii ->
                val it = items.getJSONObject(ii)
                CommandItem(label = it.getString("label"), insert = it.getString("insert"))
            }
        )
    }
}
