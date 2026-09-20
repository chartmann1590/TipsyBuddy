package com.tipsybuddy.wear.ui.screens

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.tipsybuddy.wear.data.WearDrinkPresets
import com.tipsybuddy.wear.data.WearSessionState
import com.tipsybuddy.wear.ui.theme.*
import java.util.Locale

@Composable
fun WearDashboardScreen(
    sessionState: WearSessionState,
    isPhoneConnected: Boolean,
    onNavigateToQuickLog: () -> Unit,
    onNavigateToCheckIn: () -> Unit,
    onNavigateToVitals: () -> Unit,
    onQuickAddWater: () -> Unit,
    onRequestRide: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }

    var confirmationMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(confirmationMessage) {
        if (confirmationMessage != null) {
            kotlinx.coroutines.delay(2000)
            confirmationMessage = null
        }
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(BgWearDark),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 28.dp, bottom = 32.dp, start = 10.dp, end = 10.dp)
        ) {
            // Phone Connection Status Pill
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF131A2A))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isPhoneConnected) NeonGreen else NeonGold)
                    )
                    Text(
                        text = if (isPhoneConnected) "Phone Paired" else "Standalone / Cached",
                        fontSize = 9.sp,
                        color = TextSecondary
                    )
                }
            }

            // Confirmation banner if user just logged something
            if (confirmationMessage != null) {
                item {
                    Text(
                        text = confirmationMessage ?: "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // BAC Gauge / Center Ring
            item {
                val zoneColor = Color(sessionState.bacZoneColorHex)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(105.dp)
                ) {
                    // Outer glow ring
                    Box(
                        modifier = Modifier
                            .size(105.dp)
                            .clip(CircleShape)
                            .border(width = 3.dp, color = zoneColor, shape = CircleShape)
                            .background(Color(0xFF0F172A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.3f", sessionState.currentBac) + "%",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = zoneColor
                            )
                            Text(
                                text = sessionState.bacZoneTitle.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = zoneColor
                            )
                            if (sessionState.alcoholCount > 0) {
                                Text(
                                    text = "${sessionState.alcoholCount} drinks",
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }

            // Sober countdown estimate
            if (sessionState.hoursToSober > 0.0) {
                item {
                    Card(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(0.92f),
                        backgroundPainter = CardDefaults.cardBackgroundPainter(
                            startBackgroundColor = SurfaceWearDark,
                            endBackgroundColor = SurfaceWearDark
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "⏱️ Sober at ${if (sessionState.soberTargetTimeFormatted.isNotBlank()) sessionState.soberTargetTimeFormatted else String.format(Locale.US, "in ~%.1fh", sessionState.hoursToSober)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NeonGold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Hydration: ${sessionState.hydrationScorePercent}% • 💧 ${sessionState.waterCount}",
                                fontSize = 9.sp,
                                color = SkyBlue,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Current Venue Check-In Card
            if (sessionState.latestVenueName.isNotBlank()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x3310B981))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "📍", fontSize = 11.sp)
                        Text(
                            text = sessionState.latestVenueName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeonGreen,
                            maxLines = 1
                        )
                    }
                }
            }

            // Action: Quick Add Drink
            item {
                Chip(
                    onClick = onNavigateToQuickLog,
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = NeonGold,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth(0.92f),
                    label = {
                        Text(
                            text = "+ Quick Add Drink",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    },
                    icon = { Text(text = "🍻", fontSize = 16.sp) }
                )
            }

            // Action: Fast 1-Tap Water Log
            item {
                Chip(
                    onClick = {
                        try {
                            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        } catch (_: Exception) {}
                        onQuickAddWater()
                        confirmationMessage = if (isPhoneConnected) "💧 Water Logged! +Hydration" else "💧 Water Queued (Offline)"
                    },
                    colors = ChipDefaults.secondaryChipColors(
                        backgroundColor = Color(0xFF0284C7),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(0.92f),
                    label = {
                        Text(
                            text = "💧 1-Tap Water (+1)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = "Boost hydration score",
                            fontSize = 9.sp,
                            color = Color(0xFFBAE6FD)
                        )
                    }
                )
            }

            // Action: Check In
            item {
                Chip(
                    onClick = onNavigateToCheckIn,
                    colors = ChipDefaults.secondaryChipColors(
                        backgroundColor = SurfaceWearDark,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(0.92f),
                    label = {
                        Text(
                            text = "📍 Check In Venue",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                )
            }

            // Action: Vitals & Heart Rate
            item {
                Chip(
                    onClick = onNavigateToVitals,
                    colors = ChipDefaults.secondaryChipColors(
                        backgroundColor = SurfaceWearDark,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth(0.92f),
                    label = {
                        Text(
                            text = "❤️ Health Vitals",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = "HR & steps sync",
                            fontSize = 9.sp,
                            color = TextSecondary
                        )
                    }
                )
            }

            // Action: Request Ride Home
            item {
                Chip(
                    onClick = {
                        try {
                            vibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                        } catch (_: Exception) {}
                        onRequestRide()
                        confirmationMessage = if (isPhoneConnected) "🚕 Ride Summon Sent to Phone!" else "🚕 Ride Queued (Syncs when connected)"
                    },
                    colors = ChipDefaults.secondaryChipColors(
                        backgroundColor = Color(0x33EF4444),
                        contentColor = CoralRed
                    ),
                    modifier = Modifier.fillMaxWidth(0.92f),
                    label = {
                        Text(
                            text = "🚕 Summon Ride Home",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = CoralRed
                        )
                    }
                )
            }
        }
    }
}
