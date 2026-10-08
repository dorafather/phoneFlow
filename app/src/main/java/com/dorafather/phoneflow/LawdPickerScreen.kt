package com.dorafather.phoneflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
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
import com.dorafather.phoneflow.net.LawdCodeStore
import java.io.File

/**
 * "실거래가 지역추가"용 법정동 검색 화면 - 2026-10-08 dorafather 요청("신남동"
 * 검색 → 인천시/서울시 등 동명 후보가 뜨고 사용자가 고르게). 동/리 이름으로
 * 검색해도 실제로 등록되는 건 그 상위 시군구(MOLIT API가 받는 단위, 5자리
 * LAWD_CD)다 - 후보 목록에 "검색어가 속한 시군구"를 같이 보여줘 혼동을
 * 줄인다. 고른 결과는 "이름@코드"(예: "미추홀구@28177") 한 토큰으로 인코딩해
 * 돌려준다 - 공백이 없어 rest.sce의 단어분리가 안전하게 한 단어로 받고,
 * rest.sce 쪽은 더 이상 지역명을 하드코딩한 분기표로 풀이할 필요가 없다
 * (전국 어디든 이 화면이 이미 다 풀어서 넘겨주므로).
 */
@Composable
fun LawdPickerScreen(filesDir: File, onPicked: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { LawdCodeStore.search(filesDir, query) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "동/읍/면/리 또는 구/시 이름으로 검색하세요",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            placeholder = { Text("예: 신남동, 강남구") },
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
                        .clickable { onPicked("${match.parentDisplayName}@${match.parentCode5}") }
                        .padding(vertical = 10.dp)
                ) {
                    Text(match.fullName, style = MaterialTheme.typography.bodyLarge)
                    if (match.fullName.split(" ").last() != match.parentDisplayName) {
                        Text(
                            "등록될 지역: ${match.parentDisplayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Divider()
            }
        }
    }
}
