package com.dorafather.phoneflow

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 서비스별(행사/주유소/메뉴/주차장) 체크박스로 켜고 끄며 한 지도에서 동시에
 * 둘러보는 화면(2026-10-25 "맵 서비스처럼 체크박스로 토글" 요청). 기존
 * 채팅 응답 하나짜리 "지도로 보기"(NearbyMapScreen)와는 완전히 별개로
 * 공존한다 - 이 화면은 드로워의 "🗺️ 지도" 메뉴로 독립 진입.
 *
 * 체크박스를 끄면 그 카테고리 핀만 지도에서 숨기고, 조회했던 데이터는
 * 캐시에 남겨둔다(다시 켜면 재조회 없이 즉시 다시 보임) - 화면을 나가면
 * 캐시도 같이 사라짐(다음 진입 때 새로 조회).
 */
private enum class MapCategory(val label: String, val hue: Float) {
    FESTIVAL("행사", BitmapDescriptorFactory.HUE_VIOLET),
    GAS("주유소", BitmapDescriptorFactory.HUE_ORANGE),
    MENU("메뉴", BitmapDescriptorFactory.HUE_GREEN),
    PARKING("주차장", BitmapDescriptorFactory.HUE_AZURE)
}

private data class CategoryMapPin(val pin: MapPin, val category: MapCategory)

private fun hueToSwatchColor(hue: Float): Color {
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 0.9f))
    return Color(argb)
}

@Composable
fun MapBrowserScreen(
    onFetchFestival: suspend (Double, Double) -> List<MapPin>,
    onFetchGas: suspend (Double, Double) -> List<MapPin>,
    onFetchMenu: suspend (Double, Double) -> List<MapPin>,
    onFetchParking: suspend (Double, Double) -> List<MapPin>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var userLocation by remember { mutableStateOf<Location?>(null) }
    var locationFailed by remember { mutableStateOf(false) }
    var hasLocationPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val checked = remember { mutableStateMapOf<MapCategory, Boolean>() }
    val cache = remember { mutableStateMapOf<MapCategory, List<MapPin>>() }
    val loading = remember { mutableStateMapOf<MapCategory, Boolean>() }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
        if (granted) {
            requestFreshLocation(context) { loc ->
                userLocation = loc
                locationFailed = (loc == null)
            }
        } else {
            locationFailed = true
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            requestFreshLocation(context) { loc ->
                userLocation = loc
                locationFailed = (loc == null)
            }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun fetcherFor(category: MapCategory): suspend (Double, Double) -> List<MapPin> = when (category) {
        MapCategory.FESTIVAL -> onFetchFestival
        MapCategory.GAS -> onFetchGas
        MapCategory.MENU -> onFetchMenu
        MapCategory.PARKING -> onFetchParking
    }

    fun toggle(category: MapCategory) {
        val next = !(checked[category] ?: false)
        checked[category] = next
        val loc = userLocation
        if (next && cache[category] == null && loc != null) {
            loading[category] = true
            scope.launch(Dispatchers.IO) {
                val result = try {
                    fetcherFor(category)(loc.latitude, loc.longitude)
                } catch (_: Throwable) {
                    emptyList()
                }
                cache[category] = result
                loading[category] = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            MapCategory.values().forEach { category ->
                Row(
                    modifier = Modifier.padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = checked[category] ?: false,
                        onCheckedChange = { toggle(category) },
                        colors = androidx.compose.material3.CheckboxDefaults.colors(
                            checkedColor = hueToSwatchColor(category.hue)
                        )
                    )
                    Text(category.label, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (locationFailed) {
            Text(
                "현재 위치를 확인하지 못했습니다. 위치 권한/GPS 신호를 확인해주세요.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val visiblePins = MapCategory.values()
                .filter { checked[it] == true }
                .flatMap { category -> (cache[category] ?: emptyList()).map { CategoryMapPin(it, category) } }
            CategoryMapView(pins = visiblePins, hasLocationPermission = hasLocationPermission)
            if (loading.values.any { it }) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp))
                        Text(
                            " 조회 중...",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryMapView(pins: List<CategoryMapPin>, hasLocationPermission: Boolean) {
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(pins) {
        if (pins.isEmpty()) return@LaunchedEffect
        if (pins.size == 1) {
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                    LatLng(pins[0].pin.lat, pins[0].pin.lon), 15f
                )
            )
        } else {
            val bounds = com.google.android.gms.maps.model.LatLngBounds.Builder().apply {
                pins.forEach { include(LatLng(it.pin.lat, it.pin.lon)) }
            }.build()
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(bounds, 120)
            )
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
        uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission)
    ) {
        pins.forEach { item ->
            Marker(
                state = MarkerState(position = LatLng(item.pin.lat, item.pin.lon)),
                title = item.pin.name,
                snippet = item.pin.subtitle.ifBlank { item.category.label },
                icon = BitmapDescriptorFactory.defaultMarker(item.category.hue)
            )
        }
    }
}
