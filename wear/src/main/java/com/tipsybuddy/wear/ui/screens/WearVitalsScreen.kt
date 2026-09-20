package com.tipsybuddy.wear.ui.screens

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import com.tipsybuddy.wear.ui.theme.*

@Composable
fun WearVitalsScreen(
    currentHeartRate: Int,
    peakHeartRate: Int,
    stepsTonight: Int,
    hasHeartRateSensor: Boolean,
    onForceSync: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Vibrator::class.java) }
    var syncFeedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(syncFeedback) {
        if (syncFeedback != null) {
            kotlinx.coroutines.delay(1800)
            syncFeedback = null
        }
    }

    // Heartbeat pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (currentHeartRate > 90) 500 else 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )

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
            contentPadding = PaddingValues(top = 26.dp, bottom = 32.dp, start = 10.dp, end = 10.dp)
        ) {
            item {
                Text(
                    text = "Live Health Vitals",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            if (syncFeedback != null) {
                item {
                    Text(
                        text = syncFeedback ?: "",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                }
            }

            // Live Heart Rate Card
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .scale(pulseScale)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x33EF4444)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "❤️", fontSize = 14.sp)
                            }

                            Text(
                                text = if (currentHeartRate > 0) "$currentHeartRate BPM" else if (hasHeartRateSensor) "Measuring..." else "-- BPM",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (currentHeartRate >= 95) CoralRed else TextPrimary
                            )
                        }

                        if (currentHeartRate >= 95) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚠️ Elevated HR: Alcohol causes cardiac vasodilation. Drink water to stabilize!",
                                fontSize = 9.sp,
                                color = CoralRed,
                                textAlign = TextAlign.Center
                            )
                        } else if (peakHeartRate > 0) {
                            Text(
                                text = "Peak tonight: $peakHeartRate BPM",
                                fontSize = 9.sp,
                                color = TextSecondary
                            )
                        } else if (!hasHeartRateSensor) {
                            Text(
                                text = "Sensor Unavailable",
                                fontSize = 9.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // Step Count Card
            item {
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(0.92f),
                    backgroundPainter = CardDefaults.cardBackgroundPainter(
                        startBackgroundColor = SurfaceWearDark,
                        endBackgroundColor = SurfaceWearDark
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "👟", fontSize = 18.sp)
                        Column {
                            Text(
                                text = "$stepsTonight steps",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Night-out activity",
                                fontSize = 9.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // Force Sync Button
            item {
                Chip(
                    onClick = {
                        try {
                            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        } catch (_: Exception) {}
                        onForceSync()
                        syncFeedback = "✓ Vitals Synced to Phone!"
                    },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = NeonCyan,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.fillMaxWidth(0.92f),
                    label = {
                        Text(
                            text = "Sync Vitals to Phone",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                )
            }
        }
    }
}
