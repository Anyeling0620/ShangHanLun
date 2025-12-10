package com.shuati.shanghanlun.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.shuati.shanghanlun.data.local.FontManager

// 定义深色模式的配色方案
private val DarkColorScheme = darkColorScheme(
    primary = ZenGreenPrimary,
    secondary = ZenGreenAccent,
    tertiary = ZenCream,
    background = ZenDark,
    surface = ZenDark,
    onPrimary = Color.White,
    onSecondary = ZenDark,
    onTertiary = ZenDark,
    onBackground = Color.White,
    onSurface = Color.White,
)

// 定义浅色模式的配色方案
private val LightColorScheme = lightColorScheme(
    primary = ZenGreenPrimary,
    secondary = ZenGreenAccent,
    tertiary = ZenCream,
    background = ZenBackgroundStart,
    surface = ZenSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = ZenDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun KillQuestionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Android 12+ 的动态取色功能 (Dynamic Color)
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 设置状态栏颜色
            window.statusBarColor = colorScheme.primary.toArgb()
            // 设置状态栏图标颜色 (浅色模式下图标为深色)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = getAppTypography(FontManager.currentFontFamily), // 注意：如果报错 'Typography' 未找到，请看下面的说明
        content = content
    )
}