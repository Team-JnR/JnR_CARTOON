package io.github.picturehouse.cartoon.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object CartoonColors {
    val Bg = Color(0xFF14161C)
    val Surface = Color(0xFF1E212A)
    val SurfaceHigh = Color(0xFF2A2E3A)
    val Yellow = Color(0xFFFFD43B)
    val Pink = Color(0xFFFF6B9A)
    val Mint = Color(0xFF4FE3C1)
    val Sky = Color(0xFF6EA8FF)
    val TextDim = Color(0xFFA6ABBA)
}

private val scheme = darkColorScheme(
    primary = CartoonColors.Yellow,
    onPrimary = Color(0xFF1A1500),
    secondary = CartoonColors.Mint,
    onSecondary = Color(0xFF00201A),
    tertiary = CartoonColors.Pink,
    background = CartoonColors.Bg,
    surface = CartoonColors.Surface,
    surfaceVariant = CartoonColors.SurfaceHigh,
    surfaceContainer = CartoonColors.Surface,
    surfaceContainerHigh = CartoonColors.SurfaceHigh,
    onSurface = Color(0xFFF2F3F7),
    onSurfaceVariant = CartoonColors.TextDim,
)

// 차량 디스플레이는 시선 거리가 멀어 글자를 크게 쓴다
private val type = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Black),
    displayMedium = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 22.sp),
    bodyMedium = TextStyle(fontSize = 20.sp),
    bodySmall = TextStyle(fontSize = 18.sp),
    labelLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun CartoonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
