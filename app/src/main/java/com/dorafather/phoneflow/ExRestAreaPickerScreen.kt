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
import com.dorafather.phoneflow.net.ExRestAreaStore
import java.io.File

@Composable
fun ExRestAreaPickerScreen(filesDir: File, onPicked: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { ExRestAreaStore.search(filesDir, query) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "휴게소 이름으로 검색하세요",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
            placeholder = { Text("예: 안성") },
            singleLine = true
        )
        if (query.isNotBlank() && results.isEmpty()) {
            Text(
                "일치하는 휴게소가 없습니다",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results) { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPicked(name) }
                        .padding(vertical = 10.dp)
                )
                HorizontalDivider()
            }
        }
    }
}
