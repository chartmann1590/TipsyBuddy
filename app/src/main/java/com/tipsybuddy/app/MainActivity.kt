package com.tipsybuddy.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tipsybuddy.app.data.*
import com.tipsybuddy.app.ui.screens.*
import com.tipsybuddy.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    TONIGHT("Tonight", Icons.Filled.LocalBar, Icons.Outlined.LocalBar),
    LOG("Log", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline),
    CALENDAR("Calendar", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    HEALTH("Health", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    VENUES("Live Share", Icons.Filled.LocationOn, Icons.Outlined.LocationOn),
    RIDES("Rides", Icons.Filled.DirectionsCar, Icons.Outlined.DirectionsCar),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request location permissions if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        val db = AppDatabase.getInstance(this)
        val userPrefs = UserPreferences(this)

        setContent {
            TipsyBuddyTheme {
                MainAppContent(db = db, userPrefs = userPrefs)
            }
        }
    }
}

@Composable
fun MainAppContent(
    db: AppDatabase,
    userPrefs: UserPreferences
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(AppScreen.TONIGHT) }

    val todayStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    // Collect Room Flows
    val tonightDrinks by db.drinkDao().getDrinksForSession(todayStr).collectAsState(initial = emptyList())
    val allDrinks by db.drinkDao().getAllDrinks().collectAsState(initial = emptyList())
    val latestCheckIn by db.checkInDao().getLatestCheckIn().collectAsState(initial = null)


    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = SurfaceDark,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppScreen.values().forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonGold,
                            selectedTextColor = NeonGold,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = Color(0x33F59E0B)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                AppScreen.TONIGHT -> {
                    TonightDashboardScreen(
                        drinks = tonightDrinks,
                        latestCheckIn = latestCheckIn,
                        userPrefs = userPrefs,
                        onAddDrink = { name, category, vol, abv, price ->
                            coroutineScope.launch {
                                db.drinkDao().insertDrink(
                                    DrinkEntity(
                                        name = name,
                                        category = category,
                                        volumeOz = vol,
                                        abv = abv,
                                        price = price,
                                        timestamp = System.currentTimeMillis(),
                                        sessionDate = todayStr
                                    )
                                )
                                com.tipsybuddy.app.widget.TipsyWidgetProvider.updateAllWidgets(context)
                            }
                        },
                        onNavigateToVenues = { currentScreen = AppScreen.VENUES },
                        onNavigateToRides = { currentScreen = AppScreen.RIDES },
                        onNavigateToLog = { currentScreen = AppScreen.LOG }
                    )
                }

                AppScreen.LOG -> {
                    LogDrinksScreen(
                        drinks = tonightDrinks,
                        onAddDrink = { name, category, vol, abv, price ->
                            coroutineScope.launch {
                                db.drinkDao().insertDrink(
                                    DrinkEntity(
                                        name = name,
                                        category = category,
                                        volumeOz = vol,
                                        abv = abv,
                                        price = price,
                                        timestamp = System.currentTimeMillis(),
                                        sessionDate = todayStr
                                    )
                                )
                                com.tipsybuddy.app.widget.TipsyWidgetProvider.updateAllWidgets(context)
                            }
                        },
                        onDeleteDrink = { drink ->
                            coroutineScope.launch {
                                db.drinkDao().deleteDrink(drink)
                                com.tipsybuddy.app.widget.TipsyWidgetProvider.updateAllWidgets(context)
                            }
                        }
                    )
                }

                AppScreen.CALENDAR -> {
                    CalendarHistoryScreen(
                        allDrinks = allDrinks,
                        userWeightLbs = userPrefs.weightLbs,
                        userGender = userPrefs.gender
                    )
                }

                AppScreen.HEALTH -> {
                    HealthInsightsScreen(
                        drinks = tonightDrinks,
                        userPrefs = userPrefs
                    )
                }

                AppScreen.VENUES -> {
                    VenueCheckInScreen(
                        drinks = tonightDrinks,
                        latestCheckIn = latestCheckIn,
                        userPrefs = userPrefs,
                        onSaveCheckIn = { name, address, lat, lon ->
                            coroutineScope.launch {
                                db.checkInDao().insertCheckIn(
                                    CheckInEntity(
                                        venueName = name,
                                        address = address,
                                        latitude = lat,
                                        longitude = lon,
                                        sessionDate = todayStr
                                    )
                                )
                            }
                        }
                    )
                }

                AppScreen.RIDES -> {
                    RideSafetyScreen(
                        userPrefs = userPrefs,
                        latestCheckIn = latestCheckIn
                    )
                }

                AppScreen.PROFILE -> {
                    ProfileSettingsScreen(
                        userPrefs = userPrefs,
                        onClearAllData = {
                            coroutineScope.launch {
                                db.drinkDao().clearAll()
                                db.checkInDao().clearAll()
                                com.tipsybuddy.app.widget.TipsyWidgetProvider.updateAllWidgets(context)
                            }
                        }
                    )
                }
            }
        }
    }
}
