package com.tipsybuddy.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val BgWearDark = Color(0xFF080D1A)
val SurfaceWearDark = Color(0xFF131A2A)
val CardBorderWear = Color(0xFF1E293B)

val NeonGold = Color(0xFFF59E0B)
val NeonGreen = Color(0xFF10B981)
val NeonCyan = Color(0xFF06B6D4)
val SkyBlue = Color(0xFF38BDF8)
val CoralRed = Color(0xFFEF4444)
val DeepRed = Color(0xFFDC2626)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

val WearColorPalette = Colors(
    primary = NeonGold,
    primaryVariant = Color(0xFFD97706),
    secondary = NeonCyan,
    secondaryVariant = Color(0xFF0284C7),
    background = BgWearDark,
    surface = SurfaceWearDark,
    error = CoralRed,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onError = Color.White
)

@Composable
fun TipsyWearTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = WearColorPalette,
        content = content
    )
}
