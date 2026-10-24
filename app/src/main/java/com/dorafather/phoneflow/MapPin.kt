package com.dorafather.phoneflow

/** "내 주변" 계열 응답 하나가 "지도로 보기"에 띄울 핀 하나. */
data class MapPin(val name: String, val lat: Double, val lon: Double, val subtitle: String = "")
