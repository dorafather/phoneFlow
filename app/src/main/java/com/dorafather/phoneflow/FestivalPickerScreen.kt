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
import com.dorafather.phoneflow.net.LawdCodeStore
import java.io.File

/**
 * "행사 지역 선택"용 검색 화면 - 2026-10-09 요청(한국관광공사 TourAPI 행사정보
 * 조회의 법정동 시도/시군구코드가 MOLIT 법정동코드 앞 5자리와 같은 체계임을
 * 확인, 새 DB 없이 기존 lawd_codes.db를 재사용). LawdCodeStore가 돌려주는
 * 5자리 코드(parentCode5)를 앞 2자리(lDongRegnCd)/뒤 3자리(lDongSignguCd)로
 * 쪼개 "이름@시도코드@시군구코드" 토큰으로 돌려준다 - rest.sce가 "@"로 한
 * 번에 쪼개 SIZE==3이면 바로 지역코드로 쓰도록(KmaPickerScreen과 동일 패턴).
 */
@Composable
fun FestivalPickerScreen(filesDir: File, onPicked: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { LawdCodeStore.search(filesDir, query) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "동/읍/면 또는 구/시 이름으로 검색하세요",
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
                        .clickable {
                            val regnCd = match.parentCode5.take(2)
                            val signguCd = match.parentCode5.substring(2, 5)
                            onPicked("${match.parentDisplayName}@$regnCd@$signguCd")
                        }
                        .padding(vertical = 10.dp)
                ) {
                    Text(match.fullName, style = MaterialTheme.typography.bodyLarge)
                    if (match.fullName.split(" ").last() != match.parentDisplayName) {
                        Text(
                            "조회될 지역: ${match.parentDisplayName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
