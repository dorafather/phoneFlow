package com.dorafather.phoneflow

/**
 * "내 주변" 계열 응답 하나가 "지도로 보기"에 띄울 핀 하나.
 * detailLines는 "지도" 체크박스 브라우저(MapBrowserScreen)의 핀 클릭 상세
 * 바텀시트 전용(2026-10-25 추가) - 기본값 빈 리스트라 다른 모든 기존
 * 용도(채팅 markers, NearbyMapScreen)는 전혀 영향받지 않는다.
 */
data class MapPin(
    val name: String,
    val lat: Double,
    val lon: Double,
    val subtitle: String = "",
    val detailLines: List<String> = emptyList()
)
