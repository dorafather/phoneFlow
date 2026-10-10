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
import com.dorafather.phoneflow.net.HiraRegionCodeStore
import java.io.File

/**
 * "응급실"/"당직병원"용 지역 검색 화면 - 2026-10-11 추가. EGEN(국립중앙의료원
 * 응급의료정보)은 Q0/STAGE1(시도)을 코드가 아니라 "경기도" 같은 공식 명칭
 * 문자열 그대로 요구하는데, 사용자가 채팅으로 "응급실 경기도 하남시"처럼
 * 시도+시군구를 정확한 공식 명칭으로 두 단어 입력해야 했던 1차 구현은 너무
 * 깨지기 쉬웠다("서울시"/"서울" 등 축약형은 전부 실패) - 병원(HIRA)과 같은
 * 지역 검색 화면으로 시군구 이름 하나만 고르면 시도를 자동으로 붙여준다.
 *
 * hira_region_codes.db를 그대로 재사용한다(이미 전국 시군구+6자리 코드를
 * 갖고 있음) - 코드 앞 2자리로 시도명을 알아내는 [HiraRegionCodeStore.sidoNameForCode]만
 * 새로 추가했다. "이름@시도명" 토큰을 내보내면 rest.sce가 @로 분리해서
 * Q0/STAGE1=시도명, Q1/STAGE2=이름으로 바로 쓴다.
 */
@Composable
fun EgenPickerScreen(filesDir: File, onPicked: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { HiraRegionCodeStore.search(filesDir, query) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "구/시/군 이름으로 검색하세요",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            placeholder = { Text("예: 하남시") },
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
                val sido = HiraRegionCodeStore.sidoNameForCode(match.code)
                if (sido != null) {
                    Text(
                        "${match.name} ($sido)",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPicked("${match.name}@$sido")
                            }
                            .padding(vertical = 10.dp)
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
