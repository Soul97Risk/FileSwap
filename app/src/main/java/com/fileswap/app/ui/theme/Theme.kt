package com.fileswap.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * M3 标准形状档位：全部控件圆角从主题取档（shapes.medium/large/extraLarge），
 * 不再散落硬编码。档位值按 M3 形状 token：4 / 8 / 12 / 16 / 28。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
    // M3E 新增档位：更圆的容器是 Expressive 的视觉签名，
    // 大面积卡片用 extraLargeIncreased，列表行/次级容器用 largeIncreased
    largeIncreased = RoundedCornerShape(20.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp)
)

@Composable
fun ShizukuFileToolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 默认关闭动态取色：默认 true 时 Android 12+ 会用壁纸取色整个顶掉下面这套
    // 补满角色的品牌色板，表现为「看不到 M3E 观感」。需要的用户可在设置里开启。
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Android 12+ 且用户显式开启时才走壁纸取色；否则用品牌种子色板
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    // Material 3 Expressive：expressive 动效曲线 + 标准形状档位 + 标准色板 + 标准字阶
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
