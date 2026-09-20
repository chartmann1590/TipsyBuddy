package com.tipsybuddy.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tipsybuddy.app.data.CheckInEntity
import com.tipsybuddy.app.data.UserPreferences
import com.tipsybuddy.app.domain.RideFareEstimate
import com.tipsybuddy.app.domain.RideManager
import com.tipsybuddy.app.ui.theme.*
import java.util.Locale

@Composable
fun RideSafetyScreen(
    userPrefs: UserPreferences,
    latestCheckIn: CheckInEntity?
) {
    val context = LocalContext.current

    var homeAddress by remember { mutableStateOf(userPrefs.homeAddress) }
    var preferredRide by remember { mutableStateOf(userPrefs.preferredRide) }
    var showEditAddressDialog by remember { mutableStateOf(false) }

    val currentLat = latestCheckIn?.latitude ?: 0.0
    val currentLon = latestCheckIn?.longitude ?: 0.0

    val fareEstimates = remember(currentLat, currentLon, userPrefs.homeLat, userPrefs.homeLon) {
        RideManager.getEstimatedFares(
            pickupLat = currentLat,
            pickupLon = currentLon,
            destLat = userPrefs.homeLat,
            destLon = userPrefs.homeLon
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column {
            Text(
                text = "Safe Ride Home",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Auto-dispatch rides with pre-filled destination & fare estimates",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Saved Home Address Destination Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x33F59E0B),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = NeonGold)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HOME DESTINATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (homeAddress.isNotBlank()) homeAddress else "No home address set (Tap edit)",
                        fontSize = 14.sp,
                        fontWeight = if (homeAddress.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                        color = if (homeAddress.isNotBlank()) TextPrimary else TextMuted,
                        maxLines = 2
                    )
                }

                IconButton(onClick = { showEditAddressDialog = true }) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Home", tint = NeonCyan)
                }
            }
        }

        // Preferred Service Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PREFERRED RIDE SERVICE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isUber = preferredRide.equals("UBER", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isUber) Color.Black else Color(0xFF0C1220),
                        border = BorderStroke(1.dp, if (isUber) NeonGold else CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                preferredRide = "UBER"
                                userPrefs.preferredRide = "UBER"
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "🚗 Uber", fontWeight = FontWeight.Bold, color = if (isUber) NeonGold else TextSecondary)
                        }
                    }

                    val isLyft = preferredRide.equals("LYFT", ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isLyft) Color(0x33FF00BF) else Color(0xFF0C1220),
                        border = BorderStroke(1.dp, if (isLyft) Color(0xFFFF00BF) else CardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                preferredRide = "LYFT"
                                userPrefs.preferredRide = "LYFT"
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "⚡ Lyft", fontWeight = FontWeight.Bold, color = if (isLyft) Color(0xFFFF00BF) else TextSecondary)
                        }
                    }
                }
            }
        }

        // Dynamic Fare Estimates List or Prompt
        if (homeAddress.isBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "🏡", fontSize = 36.sp)
                        Text(
                            text = "Set Your Home Address",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Add your home address above to calculate live fare estimates and 1-tap rides to your door.",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { showEditAddressDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Set Home Address", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            Text(
                text = "ESTIMATED FARES TO HOME (~${String.format(Locale.US, "%.1f", fareEstimates.firstOrNull()?.distanceMiles ?: 4.5)} mi)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(fareEstimates) { fare ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (fare.serviceName == "Uber") {
                                    RideManager.launchUber(
                                        context = context,
                                        homeAddress = userPrefs.homeAddress,
                                        homeLat = userPrefs.homeLat,
                                        homeLon = userPrefs.homeLon,
                                        currentLat = currentLat,
                                        currentLon = currentLon
                                    )
                                } else {
                                    RideManager.launchLyft(
                                        context = context,
                                        homeLat = userPrefs.homeLat,
                                        homeLon = userPrefs.homeLon,
                                        homeAddress = userPrefs.homeAddress
                                    )
                                }
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(text = fare.iconEmoji, fontSize = 26.sp)

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fare.typeName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "~${fare.durationMinutes} min trip",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$${String.format(Locale.US, "%.0f", fare.minPrice)} - $${String.format(Locale.US, "%.0f", fare.maxPrice)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeonGreen
                                )
                                Text(
                                    text = "Tap to book →",
                                    fontSize = 11.sp,
                                    color = NeonCyan
                                )
                            }
                        }
                    }
                }
            }
        }

        // Emergency Designated Driver Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16111E)),
            border = BorderStroke(1.dp, Color(0x44A855F7))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DESIGNATED DRIVER / FRIEND",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonPurple,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (userPrefs.emergencyContactName.isNotBlank()) userPrefs.emergencyContactName else "No contact set",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (userPrefs.emergencyContactPhone.isNotBlank()) userPrefs.emergencyContactPhone else "Set phone in Profile to quick-dial",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Button(
                    onClick = {
                        if (userPrefs.emergencyContactPhone.isNotBlank()) {
                            RideManager.callEmergencyContact(context, userPrefs.emergencyContactPhone)
                        } else {
                            Toast.makeText(context, "Set an emergency phone number in Profile first!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    shape = RoundedCornerShape(12.dp),
                    enabled = userPrefs.emergencyContactPhone.isNotBlank()
                ) {
                    Icon(imageVector = Icons.Default.Call, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Call", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Edit Home Address Dialog
    if (showEditAddressDialog) {
        var newAddress by remember { mutableStateOf(homeAddress) }
        AlertDialog(
            onDismissRequest = { showEditAddressDialog = false },
            containerColor = SurfaceDark,
            title = {
                Text(text = "Edit Home Address", fontWeight = FontWeight.Bold, color = TextPrimary)
            },
            text = {
                OutlinedTextField(
                    value = newAddress,
                    onValueChange = { newAddress = it },
                    label = { Text("Home Address") },
                    placeholder = { Text("e.g. 742 Evergreen Terrace, Springfield") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonGold,
                        unfocusedBorderColor = CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        homeAddress = newAddress.trim()
                        userPrefs.homeAddress = newAddress.trim()
                        if (newAddress.isNotBlank()) {
                            try {
                                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                                val results = geocoder.getFromLocationName(newAddress, 1)
                                if (!results.isNullOrEmpty()) {
                                    userPrefs.homeLat = results[0].latitude
                                    userPrefs.homeLon = results[0].longitude
                                }
                            } catch (e: Exception) {
                                // Fallback: coordinates will update on next lookup
                            }
                        }
                        showEditAddressDialog = false
                        Toast.makeText(context, "Home address updated!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGold)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditAddressDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
