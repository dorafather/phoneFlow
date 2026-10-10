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
 * "병원 지역 선택"용 검색 화면 - 2026-10-10 건강보험심사평가원 병원정보서비스
 * 추가. 처음엔 lawd_codes.db(법정동코드)를 재사용하려 했으나, 실기기로
 * 직접 조회해보니 totalCount=0이 나와 확인한 결과 심평원이 법정동코드와
 * 무관한 자체 지역코드를 쓴다는 걸 발견했다(HiraRegionCodeStore 참고) -
 * 그래서 전용 hira_region_codes.db를 새로 만들어 검색한다. 이 코드표는
 * 이미 "구/시/군" 단위 그 자체라 FestivalPickerScreen처럼 동/리 -> 상위
 * 시군구로 되짚어줄 필요가 없다.
 *
 * "이름@sgguCd"(2파트) 토큰만 쓴다 - sgguCd 6자리 자체가 전국 유일값이라
 * sidoCd는 중복 정보인데, 실기기 테스트에서 sidoCd를 같이 보내면 오히려
 * getHospBasisList가 totalCount=0을 반환하는 걸 확인해서(원인 불명, API
 * 쪽 이슈로 추정) sgguCd만 보내기로 했다.
 */
@Composable
fun HiraPickerScreen(filesDir: File, onPicked: (String) -> Unit) {
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
            placeholder = { Text("예: 강남구") },
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
                Text(
                    match.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPicked("${match.name}@${match.code}")
                        }
                        .padding(vertical = 10.dp)
                )
                HorizontalDivider()
            }
        }
    }
}
