package com.dorafather.phoneflow

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * "내 주변" 계열 응답(행사/주유소/메뉴/주차장)의 "지도로 보기" 전용 화면
 * (2026-10-25 추가). Map ID는 쓰지 않는다 - Map ID 없는 기본 지도(핀 찍기/
 * 확대축소/InfoWindow 정도)는 로드 횟수 제한 없이 완전 무료이고, 이 기능은
 * 딱 그 정도만 필요하다(고급 스타일링/Advanced Markers는 Map ID가 있어야
 * 하는데, Map ID를 쓰는 순간부터 월 1만 건 초과분이 과금 대상이 됨 -
 * 2026-10-25 Google Maps SDK 검토 결론).
 */
@Composable
fun NearbyMapScreen(pins: List<MapPin>) {
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(pins) {
        if (pins.isEmpty()) return@LaunchedEffect
        if (pins.size == 1) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(LatLng(pins[0].lat, pins[0].lon), 15f)
            )
        } else {
            val bounds = LatLngBounds.Builder().apply {
                pins.forEach { include(LatLng(it.lat, it.lon)) }
            }.build()
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        pins.forEach { pin ->
            Marker(
                state = MarkerState(position = LatLng(pin.lat, pin.lon)),
                title = pin.name,
                snippet = pin.subtitle.ifBlank { null }
            )
        }
    }
}
