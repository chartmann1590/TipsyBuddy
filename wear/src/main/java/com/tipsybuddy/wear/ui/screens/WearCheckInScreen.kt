package com.tipsybuddy.wear.ui.screens

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.tipsybuddy.wear.ui.theme.*

data class VenuePreset(val title: String, val emoji: String, val subtitle: String)

@Composable
fun WearCheckInScreen(
    isPhoneConnected: Boolean = true,
    onCheckInSelected: (venueName: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    var checkedInVenue by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(checkedInVenue) {
        if (checkedInVenue != null) {
            kotlinx.coroutines.delay(1200)
            onNavigateBack()
        }
    }

    val presets = remember {
        listOf(
            VenuePreset("At the Bar / Pub", "🍺", "Sharing location with friends"),
            VenuePreset("House Party", "🎉", "Party mode active"),
            VenuePreset("Nightclub / Lounge", "🪩", "Late night venue"),
            VenuePreset("Dinner / Food", "🍽️", "Grabbing food & water"),
            VenuePreset("In Transit / Uber", "🚗", "On the move"),
            VenuePreset("Safe at Home", "🏠", "Night concluded safely")
        )
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        if (checkedInVenue != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgWearDark),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "📍", fontSize = 32.sp)
                    Text(
                        text = if (isPhoneConnected) "Checked In!" else "Check-In Queued!",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPhoneConnected) NeonGreen else NeonGold
                    )
                    Text(
                        text = checkedInVenue ?: "",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (isPhoneConnected) "Location synced to phone" else "Syncs when phone connects",
                        fontSize = 9.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
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
                        text = "Wrist Check-In",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                items(presets) { preset ->
                    Chip(
                        onClick = {
                            try {
                                vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                            } catch (_: Exception) {}
                            onCheckInSelected(preset.title)
                            checkedInVenue = preset.title
                        },
                        colors = ChipDefaults.secondaryChipColors(
                            backgroundColor = SurfaceWearDark,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth(0.92f),
                        icon = {
                            Text(text = preset.emoji, fontSize = 16.sp)
                        },
                        label = {
                            Text(
                                text = preset.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = preset.subtitle,
                                fontSize = 9.sp,
                                color = TextMuted
                            )
                        }
                    )
                }
            }
        }
    }
}
