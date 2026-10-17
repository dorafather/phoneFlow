package com.dorafather.phoneflow.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * phoneFlow 브랜드 색상(2026-10-17 UI 개편 - "얼룩말+파란색을 최대한
 * 활용해서 귀엽게" 요청). PhoneFlowBlue는 런처 아이콘(assets/PhoneFLOW.jpg,
 * res/mipmap/ic_launcher)의 파란 원 배경에서 실측 추출한 값(#0080FF) -
 * 임의로 고른 파란색이 아니라 기존 브랜드 아이콘과 정확히 일치시켰다.
 */
val PhoneFlowBlue = Color(0xFF0080FF)
val PhoneFlowBlueLight = Color(0xFFD6ECFF)
val PhoneFlowBlueDark = Color(0xFF0057B3)
val PhoneFlowStripeGray = Color(0xFFF1F2F5)

private val PhoneFlowColorScheme = lightColorScheme(
    primary = PhoneFlowBlue,
    onPrimary = Color.White,
    primaryContainer = PhoneFlowBlueLight,
    onPrimaryContainer = PhoneFlowBlueDark,
    secondaryContainer = PhoneFlowStripeGray,
    onSecondaryContainer = Color(0xFF1C1C1E),
    background = Color.White,
    surface = Color.White,
)

// 기본 Material3 모서리보다 더 둥글게 잡아 "귀여운" 느낌을 낸다(메신저 앱들의
// 말풍선/버튼이 대체로 많이 둥근 것을 참고).
private val PhoneFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun PhoneFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PhoneFlowColorScheme,
        shapes = PhoneFlowShapes,
        content = content
    )
}
