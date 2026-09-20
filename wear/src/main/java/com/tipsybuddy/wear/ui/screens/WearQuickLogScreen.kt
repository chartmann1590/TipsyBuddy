package com.tipsybuddy.wear.ui.screens

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.tipsybuddy.wear.data.WearDrinkPresets
import com.tipsybuddy.wear.data.WearQuickDrink
import com.tipsybuddy.wear.ui.theme.*

@Composable
fun WearQuickLogScreen(
    onDrinkSelected: (WearQuickDrink) -> Unit,
    onNavigateBack: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    var loggedDrinkName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loggedDrinkName) {
        if (loggedDrinkName != null) {
            kotlinx.coroutines.delay(1200)
            onNavigateBack()
        }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        if (loggedDrinkName != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgWearDark),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "✅", fontSize = 32.sp)
                    Text(
                        text = "$loggedDrinkName",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Logged & Synced!",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        } else {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgWearDark),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(top = 26.dp, bottom = 30.dp, start = 10.dp, end = 10.dp)
            ) {
                item {
                    Text(
                        text = "Quick Add Drink",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                items(WearDrinkPresets.PRESETS) { drink ->
                    Chip(
                        onClick = {
                            try {
                                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                            } catch (_: Exception) {}
                            onDrinkSelected(drink)
                            loggedDrinkName = drink.name
                        },
                        colors = ChipDefaults.secondaryChipColors(
                            backgroundColor = SurfaceWearDark,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(0.92f),
                        icon = {
                            Text(text = drink.emoji, fontSize = 16.sp)
                        },
                        label = {
                            Text(
                                text = drink.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = "${drink.volumeOz.toInt()} oz • ${drink.abv}% ABV",
                                fontSize = 9.sp,
                                color = Color(drink.colorHex)
                            )
                        }
                    )
                }
            }
        }
    }
}
