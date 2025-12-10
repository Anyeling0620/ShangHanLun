package com.shuati.shanghanlun.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// 基础色板
val ZenDark = Color(0xFF1C2321) // 深墨绿黑
val ZenGreenPrimary = Color(0xFF2D6A4F) // 祖母绿
val ZenGreenAccent = Color(0xFF52B788) // 清透绿
val ZenCream = Color(0xFFF0F3BD) // 奶油黄装饰
val ZenSurface = Color(0xFFFFFFFF)
val ZenBackgroundStart = Color(0xFFF1F5F9)
val ZenBackgroundEnd = Color(0xFFE2E8F0)

val TextPrimary = Color(0xFF1F2937)
val TextSecondary = Color(0xFF6B7280)

// 功能色
val ColorCorrect = Color(0xFF10B981)
val ColorWrong = Color(0xFFEF4444)
val ColorMistake = Color(0xFFF43F5E)
val ColorStar = Color(0xFFF59E0B)

// 渐变定义
val MainGradient = Brush.linearGradient(
    colors = listOf(ZenGreenPrimary, Color(0xFF1B4332)),
    start = Offset(0f, 0f),
    end = Offset(1000f, 1000f)
)

val CardGradient = Brush.verticalGradient(
    colors = listOf(Color.White, Color(0xFFF8FAFC))
)

val MistakeGradient = Brush.linearGradient(
    colors = listOf(Color(0xFFEF4444), Color(0xFFB91C1C))
)

// 炫彩文字渐变
val ColorfulGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFEC4899), // Pink
        Color(0xFF8B5CF6), // Purple
        Color(0xFF3B82F6), // Blue
        Color(0xFF10B981)  // Green
    )
)

// 定义炫彩颜色数组 (用于流光特效)
val RainbowColors = listOf(
    Color(0xFFEC4899), // Pink
    Color(0xFF8B5CF6), // Purple
    Color(0xFF3B82F6), // Blue
    Color(0xFF06B6D4), // Cyan
    Color(0xFF10B981), // Green
    Color(0xFFFACC15), // Yellow
    Color(0xFFEC4899)  // Loop back to Pink
)