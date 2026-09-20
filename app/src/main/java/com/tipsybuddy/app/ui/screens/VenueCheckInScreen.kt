package com.tipsybuddy.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tipsybuddy.app.data.CheckInEntity
import com.tipsybuddy.app.data.DrinkEntity
import com.tipsybuddy.app.data.FirebaseLiveSync
import com.tipsybuddy.app.data.UserPreferences
import com.tipsybuddy.app.domain.BacCalculator
import com.tipsybuddy.app.domain.VenueLocationResult
import com.tipsybuddy.app.domain.VenueSearchService
import com.tipsybuddy.app.ui.theme.*
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.*

private fun createVenueMarkerBitmap(context: Context): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val width = (38 * density).toInt().coerceAtLeast(38)
    val height = (52 * density).toInt().coerceAtLeast(52)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 1. Ground contact shadow
    paint.color = android.graphics.Color.argb(90, 0, 0, 0)
    paint.style = Paint.Style.FILL
    canvas.drawOval(
        width * 0.2f, height * 0.88f, width * 0.8f, height * 0.99f, paint
    )

    // 2. Pin body (Teardrop shape)
    val headRadius = width * 0.44f
    val centerX = width * 0.5f
    val centerY = headRadius + (2 * density)

    val pinPath = Path()
    pinPath.addCircle(centerX, centerY, headRadius, Path.Direction.CW)

    val tipPath = Path()
    tipPath.moveTo(centerX - headRadius * 0.82f, centerY + headRadius * 0.38f)
    tipPath.lineTo(centerX + headRadius * 0.82f, centerY + headRadius * 0.38f)
    tipPath.lineTo(centerX, height * 0.88f)
    tipPath.close()

    pinPath.op(tipPath, Path.Op.UNION)

    // Fill Pin with Vibrant Red
    paint.style = Paint.Style.FILL
    paint.color = android.graphics.Color.parseColor("#E11D48")
    canvas.drawPath(pinPath, paint)

    // Pin Border (White highlight for crisp contrast against map tiles)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.5f * density
    paint.color = android.graphics.Color.WHITE
    canvas.drawPath(pinPath, paint)

    // 3. Inner circle (White circle)
    paint.style = Paint.Style.FILL
    paint.color = android.graphics.Color.WHITE
    val innerRadius = headRadius * 0.52f
    canvas.drawCircle(centerX, centerY, innerRadius, paint)

    // 4. Center Gold core
    paint.color = android.graphics.Color.parseColor("#F59E0B")
    canvas.drawCircle(centerX, centerY, innerRadius * 0.65f, paint)

    val drawable = BitmapDrawable(context.resources, bitmap)
    drawable.setBounds(0, 0, width, height)
    return drawable
}

@Composable
fun VenueCheckInScreen(
    drinks: List<DrinkEntity>,
    latestCheckIn: CheckInEntity?,
    userPrefs: UserPreferences,
    onSaveCheckIn: (venueName: String, address: String, lat: Double, lon: Double) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val searchService = remember { VenueSearchService() }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<VenueLocationResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchStatus by remember { mutableStateOf<String?>(null) }

    var venueNameInput by remember { mutableStateOf(latestCheckIn?.venueName ?: "") }
    var currentLat by remember { mutableDoubleStateOf(latestCheckIn?.latitude ?: 0.0) }
    var currentLon by remember { mutableDoubleStateOf(latestCheckIn?.longitude ?: 0.0) }
    var detectedAddress by remember { mutableStateOf(latestCheckIn?.address ?: "Search a venue or tap 'Use GPS' below") }
    var isLocating by remember { mutableStateOf(false) }

    var isLiveSharing by remember { mutableStateOf(userPrefs.isLiveSharing) }
    var selectedDurationMins by remember { mutableIntStateOf(userPrefs.liveDurationMinutes) }
    var liveStatusMessage by remember { mutableStateOf("Partying at Venue 🍸") }
    var isSyncing by remember { mutableStateOf(false) }

    val liveSync = remember { FirebaseLiveSync(context) }

    val currentBac = remember(drinks, userPrefs.weightLbs, userPrefs.gender) {
        BacCalculator.calculate(
            drinks = drinks,
            weightLbs = userPrefs.weightLbs,
            gender = userPrefs.gender
        ).bac
    }

    val liveShareUrl = "https://tipsybuddy.web.app/?session=${userPrefs.liveSessionId}"

    fun performSearch(queryText: String) {
        val trimmed = queryText.trim()
        if (trimmed.isBlank()) return
        isSearching = true
        searchStatus = null
        coroutineScope.launch {
            try {
                val results = searchService.search(trimmed, context)
                searchResults = results
                if (results.isEmpty()) {
                    searchStatus = "No matching venues or addresses found. Try a different search."
                }
            } catch (e: Exception) {
                searchStatus = "Search failed: ${e.message}"
            } finally {
                isSearching = false
            }
        }
    }

    // Function to acquire location and reverse geocode
    fun detectLocation() {
        isLocating = true
        coroutineScope.launch {
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                var loc: Location? = null
                if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                        ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }

                if (loc != null) {
                    currentLat = loc.latitude
                    currentLon = loc.longitude
                    val place = searchService.reverseGeocode(loc.latitude, loc.longitude, context)
                    detectedAddress = place.address
                    if (venueNameInput.isBlank() || venueNameInput.matches(Regex("^\\d+$"))) {
                        venueNameInput = place.title
                    }
                    Toast.makeText(context, "📍 GPS Location acquired!", Toast.LENGTH_SHORT).show()
                } else {
                    detectedAddress = "GPS location unavailable. Please ensure location is enabled."
                    Toast.makeText(context, "Could not acquire GPS fix. Please check location settings.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Location error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLocating = false
            }
        }
    }

    // Auto-detect location on launch if no previous check-in
    LaunchedEffect(Unit) {
        if (latestCheckIn == null && context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            detectLocation()
        }
    }

    // Function to push live location to Firebase
    fun syncToFirebase() {
        isSyncing = true
        coroutineScope.launch {
            val expiresAt = if (isLiveSharing) {
                System.currentTimeMillis() + (selectedDurationMins * 60 * 1000L)
            } else 0L

            val success = liveSync.syncSession(
                sessionId = userPrefs.liveSessionId,
                userName = userPrefs.userName,
                venue = venueNameInput.ifBlank { "Night Out" },
                status = liveStatusMessage,
                latitude = currentLat,
                longitude = currentLon,
                bac = currentBac,
                homeAddress = userPrefs.homeAddress,
                phone = userPrefs.emergencyContactPhone,
                expiresAtMillis = expiresAt
            )

            isSyncing = false
            if (success) {
                Toast.makeText(context, "☁️ Live location synced to website!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Sync issue, will retry", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Column {
            Text(
                text = "Venues & Live Sharing",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Check into bars & share your live location with friends",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Live Sharing Master Controller Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLiveSharing) Color(0x2210B981) else SurfaceDark
            ),
            border = BorderStroke(1.dp, if (isLiveSharing) NeonGreen else CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isLiveSharing) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(NeonGreen)
                                )
                            }
                            Text(
                                text = if (isLiveSharing) "LIVE SHARING ACTIVE" else "LIVE LOCATION SHARE",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isLiveSharing) NeonGreen else TextPrimary
                            )
                        }
                        Text(
                            text = if (isLiveSharing) "Friends can view your live map & BAC on tipsybuddy.web.app"
                            else "Share a safe link for a set time with friends",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Switch(
                        checked = isLiveSharing,
                        onCheckedChange = { checked ->
                            isLiveSharing = checked
                            userPrefs.isLiveSharing = checked
                            if (checked) {
                                val expiresAt = System.currentTimeMillis() + (selectedDurationMins * 60 * 1000L)
                                userPrefs.liveExpiresAt = expiresAt
                                syncToFirebase()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = NeonGreen
                        )
                    )
                }

                if (isLiveSharing) {
                    HorizontalDivider(color = CardBorder)

                    // Duration Picker
                    Text(
                        text = "ACTIVE DURATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(30, 60, 120, 240).forEach { mins ->
                            val isSelected = selectedDurationMins == mins
                            val label = if (mins >= 60) "${mins / 60}h" else "${mins}m"
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) NeonGreen else Color(0xFF0C1220),
                                border = BorderStroke(1.dp, if (isSelected) NeonGreen else CardBorder),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedDurationMins = mins
                                        userPrefs.liveDurationMinutes = mins
                                        userPrefs.liveExpiresAt = System.currentTimeMillis() + (mins * 60 * 1000L)
                                        syncToFirebase()
                                    }
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.Black else TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Status Message Selector
                    Text(
                        text = "STATUS MESSAGE FOR FRIENDS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )

                    val statusOptions = listOf(
                        "Partying at Venue 🍸",
                        "Grabbing Late Food 🍕",
                        "In an Uber Home 🚕",
                        "Safe at Home 🏡"
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(statusOptions) { status ->
                            val isSelected = liveStatusMessage == status
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) NeonGold else Color(0xFF0C1220),
                                border = BorderStroke(1.dp, if (isSelected) NeonGold else CardBorder),
                                modifier = Modifier.clickable {
                                    liveStatusMessage = status
                                    syncToFirebase()
                                }
                            ) {
                                Text(
                                    text = status,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else TextSecondary
                                )
                            }
                        }
                    }

                    // Share Link Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0C1220),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "LIVE SHARE URL", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                                Text(
                                    text = liveShareUrl,
                                    fontSize = 12.sp,
                                    color = NeonCyan,
                                    maxLines = 1
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("TipsyBuddy Live Link", liveShareUrl))
                                    Toast.makeText(context, "📋 Link copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = TextSecondary)
                                }

                                IconButton(onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "${userPrefs.userName}'s Live Night Out on TipsyBuddy")
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Hey! Track my live night out location and safety status on TipsyBuddy: $liveShareUrl (active for the next ${selectedDurationMins} minutes)"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Live Location with Friends"))
                                }) {
                                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share", tint = NeonGreen)
                                }
                            }
                        }
                    }

                    // Immediate Sync Button
                    Button(
                        onClick = { syncToFirebase() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isSyncing
                    ) {
                        Text(
                            text = if (isSyncing) "Syncing..." else "🔄 Sync Live Location Now",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Bar Check-in Section Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "SEARCH VENUE OR ADDRESS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                // Search Bar Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        if (it.isBlank()) {
                            searchResults = emptyList()
                            searchStatus = null
                        }
                    },
                    label = { Text("Search Bar, Venue or Address") },
                    placeholder = { Text("e.g. Frog Alley, 108 State St, The Bier Abbey...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = NeonGold)
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = NeonGold,
                                strokeWidth = 2.dp
                            )
                        } else if (searchQuery.isNotBlank()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                searchResults = emptyList()
                                searchStatus = null
                            }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { performSearch(searchQuery) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonGold,
                        unfocusedBorderColor = CardBorder
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Search Action Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { performSearch(searchQuery) },
                        enabled = searchQuery.isNotBlank() && !isSearching,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Search Address", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Search Results Dropdown List
                if (searchResults.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, CardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "SELECT MATCHING VENUE (${searchResults.size})",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            searchResults.forEach { result ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF161F33),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            venueNameInput = result.title
                                            detectedAddress = result.address
                                            currentLat = result.latitude
                                            currentLon = result.longitude
                                            searchResults = emptyList()
                                            searchQuery = result.title
                                            searchStatus = null
                                            Toast.makeText(context, "📍 Selected ${result.title}", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            tint = NeonCyan,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = result.title,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = result.address,
                                                fontSize = 11.sp,
                                                color = TextSecondary,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (searchStatus != null) {
                    Text(
                        text = searchStatus!!,
                        fontSize = 12.sp,
                        color = AmberGlow,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "CHECK IN DETAILS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                OutlinedTextField(
                    value = venueNameInput,
                    onValueChange = { venueNameInput = it },
                    label = { Text("Venue / Bar Name") },
                    placeholder = { Text("e.g. Local Pub, Lounge, Nightclub") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = NeonGold,
                        unfocusedBorderColor = CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick Venue Presets
                val venuePresets = listOf("Local Pub", "Cocktail Lounge", "Sports Cantina", "Nightclub", "House Party")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(venuePresets) { preset ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0C1220),
                            border = BorderStroke(1.dp, CardBorder),
                            modifier = Modifier.clickable { venueNameInput = preset }
                        ) {
                            Text(
                                text = preset,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { detectLocation() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NeonCyan)
                    ) {
                        Icon(imageVector = Icons.Default.MyLocation, contentDescription = null, tint = NeonCyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = if (isLocating) "Locating..." else "Use GPS", color = NeonCyan)
                    }

                    Button(
                        onClick = {
                            val finalVenue = venueNameInput.ifBlank { "Night Out Venue" }
                            val finalAddress = if (detectedAddress.startsWith("Search a venue") || detectedAddress.startsWith("Tap 'Use GPS'")) "Current Location" else detectedAddress
                            onSaveCheckIn(finalVenue, finalAddress, currentLat, currentLon)
                            if (isLiveSharing) {
                                syncToFirebase()
                            }
                            Toast.makeText(context, "🍻 Checked in to $finalVenue!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGold),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "Check In", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0C1220),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(imageVector = Icons.Default.Place, contentDescription = null, tint = CoralRed, modifier = Modifier.size(16.dp))
                            Text(
                                text = detectedAddress,
                                fontSize = 12.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (currentLat != 0.0 && currentLon != 0.0) {
                            Text(
                                text = "Coordinates: ${String.format(Locale.US, "%.5f, %.5f", currentLat, currentLon)}",
                                fontSize = 10.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(start = 22.dp)
                            )
                        }
                    }
                }
            }
        }

        // Free OpenStreetMap Live View (OSMDroid)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            border = BorderStroke(1.dp, CardBorder)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = CoralRed, modifier = Modifier.size(16.dp))
                        Text(
                            text = "OPENSTREETMAP LIVE PIN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "100% Free & Open Source",
                        fontSize = 10.sp,
                        color = NeonGreen
                    )
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            isTilesScaledToDpi = true
                            onResume()
                            if (currentLat != 0.0 && currentLon != 0.0) {
                                val geoPoint = GeoPoint(currentLat, currentLon)
                                val marker = Marker(this).apply {
                                    position = geoPoint
                                    icon = createVenueMarkerBitmap(ctx)
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = venueNameInput.ifBlank { "Venue Location" }
                                    snippet = detectedAddress
                                }
                                overlays.add(marker)
                                post {
                                    controller.setZoom(16.5)
                                    controller.setCenter(geoPoint)
                                    marker.showInfoWindow()
                                }
                            } else {
                                controller.setZoom(4.0)
                                controller.setCenter(GeoPoint(39.8283, -98.5795))
                            }
                        }
                    },
                    update = { view ->
                        view.overlays.clear()
                        if (currentLat != 0.0 && currentLon != 0.0) {
                            val geoPoint = GeoPoint(currentLat, currentLon)
                            val marker = Marker(view).apply {
                                position = geoPoint
                                icon = createVenueMarkerBitmap(view.context)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = venueNameInput.ifBlank { "Venue Location" }
                                snippet = detectedAddress
                            }
                            view.overlays.add(marker)
                            view.post {
                                view.controller.setZoom(16.5)
                                view.controller.animateTo(geoPoint)
                                marker.showInfoWindow()
                            }
                        }
                        view.invalidate()
                    }
                )
            }
        }
    }
}
