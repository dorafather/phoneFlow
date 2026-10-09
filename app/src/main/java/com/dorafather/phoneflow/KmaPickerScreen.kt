package com.dorafather.phoneflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dorafather.phoneflow.net.KmaGridStore
import java.io.File

/**
 * "기상청 지역 추가"용 격자 검색 화면 - 2026-10-09 dorafather 요청(법정동코드와
 * 같은 체계의 행정구역코드를 쓰는 걸 발견, LawdPickerScreen과 동일한 패턴으로
 * 추가). MOLIT과 달리 상위 단위로 뭉칠 필요 없이 검색된 읍면동 자신의 nx,ny를
 * 그대로 등록한다. 고른 결과는 "이름@nx@ny" 토큰으로 돌려준다 - rest.sce가
 * "@"로 한 번에 쪼개 SIZE==3이면 바로 좌표로 쓰도록.
 */
@Composable
fun KmaPickerScreen(filesDir: File, onPicked: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { KmaGridStore.search(filesDir, query) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "동/읍/면 또는 구/시 이름으로 검색하세요",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            placeholder = { Text("예: 청운효자동, 종로구") },
            singleLine = true
        )
        if (query.isNotBlank() && results.isEmpty()) {
            Text(
                "일치하는 지역이 없습니다",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results) { match ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPicked("${match.fullName}@${match.nx}@${match.ny}") }
                        .padding(vertical = 10.dp)
                ) {
                    Text(match.fullName, style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider()
            }
        }
    }
}
