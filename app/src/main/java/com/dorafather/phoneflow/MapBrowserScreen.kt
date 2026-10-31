package com.dorafather.phoneflow

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dorafather.phoneflow.net.SafeMapWmsTileProvider
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileProvider
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.TileOverlay
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
    PARKING("주차장", BitmapDescriptorFactory.HUE_AZURE),
    GOCAMPING("고캠핑", BitmapDescriptorFactory.HUE_ROSE)
}

private data class CategoryMapPin(val pin: MapPin, val category: MapCategory)

private fun hueToSwatchColor(hue: Float): Color {
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 0.9f))
    return Color(argb)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapBrowserScreen(
    onFetchFestival: suspend (Double, Double) -> List<MapPin>,
    onFetchGas: suspend (Double, Double) -> List<MapPin>,
    onFetchMenu: suspend (Double, Double) -> List<MapPin>,
    onFetchParking: suspend (Double, Double) -> List<MapPin>,
    onFetchGoCamping: suspend (Double, Double) -> List<MapPin>,
    // 범죄주의구간 WMS 레이어(2026-10-31 추가) - 핀 카테고리와 달리 위치
    // 조회가 필요 없어 도메인/키만 받으면 된다. 키가 없으면(addr.ini 미설정)
    // 체크박스 자체를 안 보여준다.
    safemapDomain: String,
    safemapServiceKey: String?
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
    // 핀(마커) 클릭 상세내역 바텀시트(2026-10-25 추가) - 기본 InfoWindow
    // 대신 화면 하단에서 여러 줄 상세정보를 보여준다.
    var selectedPin by remember { mutableStateOf<CategoryMapPin?>(null) }

    // "화면을 이동하면 재검색 버튼이 있어야겠다"(2026-10-25 요청) - 지도를
    // 여기서 직접 들고 있어야 카메라 중심 좌표(재검색 기준점)와 "사용자가
    // 직접 움직였는지"를 같이 판단할 수 있다. isProgrammaticMove는 핀이 처음
    // 뜰 때 자동으로 맞추는 카메라 애니메이션(아래 fitBoundsSignal 이펙트)과
    // 사용자가 손가락으로 움직인 걸 구분하는 용도 - 이게 true인 동안의 이동은
    // 재검색 버튼을 띄우지 않는다.
    val cameraPositionState = rememberCameraPositionState()
    var isProgrammaticMove by remember { mutableStateOf(false) }
    var showResearch by remember { mutableStateOf(false) }
    // 체크박스를 막 켜서 처음 핀이 뜰 때만 카메라를 자동으로 맞춘다(아래
    // toggle()에서만 증가시킴) - "재검색"은 사용자가 고른 현재 화면 위치를
    // 존중해야 하므로 결과가 바뀌어도 카메라를 다시 맞추지 않는다.
    var fitBoundsSignal by remember { mutableStateOf(0) }

    // 범죄주의구간 WMS 레이어(2026-10-31 추가) - 점 핀이 아니라 지도 전체에
    // 겹쳐지는 이미지라 5개 유형이 서로 겹치면 뭐가 뭔지 안 보인다. 그래서
    // 체크박스가 아니라 "레이어 켜기 + 유형 하나만 선택"(라디오 버튼 식)으로
    // 둔다.
    var crimeLayerEnabled by remember { mutableStateOf(false) }
    var selectedCrimeType by remember { mutableStateOf(SafeMapWmsTileProvider.CrimeType.TOTAL) }
    // remember를 조건문 밖에서 항상 호출하고, 켜짐/키 유무 판단은 람다
    // 안에서 처리한다(Compose는 remember를 조건부로 호출하면 안 됨).
    val crimeTileProvider: TileProvider? = remember(crimeLayerEnabled, selectedCrimeType, safemapServiceKey) {
        if (crimeLayerEnabled && safemapServiceKey != null) {
            SafeMapWmsTileProvider.create(safemapDomain, safemapServiceKey, selectedCrimeType)
        } else {
            null
        }
    }

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
        MapCategory.GOCAMPING -> onFetchGoCamping
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
                fitBoundsSignal++
            }
        }
    }

    /** "이 위치에서 재검색" - 지도 중심(사용자가 손으로 옮긴 위치)을 기준으로
     *  현재 켜진 카테고리들을 전부 다시 조회한다. 카메라는 건드리지 않는다
     *  (fitBoundsSignal을 안 올림 - 사용자가 보던 화면 그대로 유지). */
    fun researchHere() {
        val center = cameraPositionState.position.target
        showResearch = false
        MapCategory.values().filter { checked[it] == true }.forEach { category ->
            loading[category] = true
            scope.launch(Dispatchers.IO) {
                val result = try {
                    fetcherFor(category)(center.latitude, center.longitude)
                } catch (_: Throwable) {
                    emptyList()
                }
                cache[category] = result
                loading[category] = false
            }
        }
    }

    LaunchedEffect(fitBoundsSignal) {
        if (fitBoundsSignal == 0) return@LaunchedEffect
        val pins = MapCategory.values()
            .filter { checked[it] == true }
            .flatMap { category -> (cache[category] ?: emptyList()).map { CategoryMapPin(it, category) } }
        if (pins.isEmpty()) return@LaunchedEffect
        isProgrammaticMove = true
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
        isProgrammaticMove = false
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && !isProgrammaticMove) {
            showResearch = true
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
        // 범죄주의구간 WMS 레이어 토글(2026-10-31 추가) - addr.ini에 SAFEMAP
        // 키가 없으면 아예 안 보여준다(설정 안 된 기능을 보여줘 혼란 주지 않음).
        if (safemapServiceKey != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = crimeLayerEnabled, onCheckedChange = { crimeLayerEnabled = it })
                Text("범죄주의구간", style = MaterialTheme.typography.bodySmall)
                if (crimeLayerEnabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    SafeMapWmsTileProvider.CrimeType.values().forEach { type ->
                        Surface(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable { selectedCrimeType = type },
                            color = if (type == selectedCrimeType) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                type.label,
                                color = if (type == selectedCrimeType) Color.White else Color.Unspecified,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
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
            CategoryMapView(
                pins = visiblePins,
                hasLocationPermission = hasLocationPermission,
                cameraPositionState = cameraPositionState,
                crimeTileProvider = crimeTileProvider,
                onMarkerClick = { selectedPin = it }
            )
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
            } else if (showResearch && checked.values.any { it }) {
                // "화면을 이동하면 재검색 버튼이 있어야겠다"(2026-10-25 요청) -
                // 네이버/카카오맵처럼 사용자가 직접 지도를 움직였을 때만 뜬다
                // (fitBoundsSignal 이펙트의 isProgrammaticMove 판별 참고).
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { researchHere() },
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        "🔄 이 위치에서 재검색",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    val detailPin = selectedPin
    if (detailPin != null) {
        ModalBottomSheet(onDismissRequest = { selectedPin = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(12.dp),
                        color = hueToSwatchColor(detailPin.category.hue),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ) {}
                    Text(
                        detailPin.category.label,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(detailPin.pin.name, style = MaterialTheme.typography.titleMedium)
                if (detailPin.pin.imageUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AsyncImage(
                        model = detailPin.pin.imageUrl,
                        contentDescription = detailPin.pin.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(MaterialTheme.shapes.medium)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                detailPin.pin.detailLines.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CategoryMapView(
    pins: List<CategoryMapPin>,
    hasLocationPermission: Boolean,
    cameraPositionState: com.google.maps.android.compose.CameraPositionState,
    crimeTileProvider: TileProvider?,
    onMarkerClick: (CategoryMapPin) -> Unit
) {
    // 카메라 자동 맞춤(최초 핀 표시)과 사용자 드래그 감지는 MapBrowserScreen이
    // cameraPositionState를 직접 들고 처리한다(2026-10-25 "재검색" 버튼
    // 추가 - 카메라 중심 좌표/이동 판별을 거기서 같이 해야 해서 끌어올림).
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
        uiSettings = MapUiSettings(myLocationButtonEnabled = hasLocationPermission)
    ) {
        // 범죄주의구간 WMS 타일은 점 핀보다 먼저(아래 레이어로) 그려야 핀이
        // 위에 보인다(2026-10-31 추가).
        if (crimeTileProvider != null) {
            TileOverlay(tileProvider = crimeTileProvider)
        }
        pins.forEach { item ->
            Marker(
                state = MarkerState(position = LatLng(item.pin.lat, item.pin.lon)),
                title = item.pin.name,
                snippet = item.pin.subtitle.ifBlank { item.category.label },
                icon = BitmapDescriptorFactory.defaultMarker(item.category.hue),
                onClick = {
                    onMarkerClick(item)
                    true
                }
            )
        }
    }
}
