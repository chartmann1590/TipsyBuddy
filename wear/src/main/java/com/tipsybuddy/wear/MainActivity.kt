package com.tipsybuddy.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.tipsybuddy.wear.data.WearDrinkPresets
import com.tipsybuddy.wear.sensor.HeartRateSensorManager
import com.tipsybuddy.wear.sync.WearSyncManager
import com.tipsybuddy.wear.ui.screens.*
import com.tipsybuddy.wear.ui.theme.TipsyWearTheme

class MainActivity : ComponentActivity() {

    private lateinit var syncManager: WearSyncManager
    private lateinit var sensorManager: HeartRateSensorManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        syncManager = WearSyncManager.getInstance(this)
        sensorManager = HeartRateSensorManager(this)

        setContent {
            TipsyWearTheme {
                val navController = rememberSwipeDismissableNavController()
                val sessionState by syncManager.sessionState.collectAsState()
                val isPhoneConnected by syncManager.isPhoneConnected.collectAsState()
                val currentHeartRate by sensorManager.currentHeartRate.collectAsState()
                val peakHeartRate by sensorManager.peakHeartRate.collectAsState()
                val stepsTonight by sensorManager.stepsTonight.collectAsState()

                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = "dashboard"
                ) {
                    composable("dashboard") {
                        WearDashboardScreen(
                            sessionState = sessionState,
                            isPhoneConnected = isPhoneConnected,
                            onNavigateToQuickLog = { navController.navigate("quick_log") },
                            onNavigateToCheckIn = { navController.navigate("check_in") },
                            onNavigateToVitals = { navController.navigate("vitals") },
                            onQuickAddWater = {
                                val waterPreset = WearDrinkPresets.PRESETS.first { it.id == "water" }
                                syncManager.sendQuickAddDrink(waterPreset)
                            },
                            onRequestRide = {
                                syncManager.sendRequestRide()
                            }
                        )
                    }

                    composable("quick_log") {
                        WearQuickLogScreen(
                            onDrinkSelected = { drink ->
                                syncManager.sendQuickAddDrink(drink)
                            },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("check_in") {
                        WearCheckInScreen(
                            onCheckInSelected = { venueName ->
                                syncManager.sendQuickCheckIn(venueName)
                            },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("vitals") {
                        WearVitalsScreen(
                            currentHeartRate = currentHeartRate,
                            peakHeartRate = peakHeartRate,
                            stepsTonight = stepsTonight,
                            hasHeartRateSensor = sensorManager.hasHeartRateSensor,
                            onForceSync = {
                                sensorManager.syncCurrentVitals()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        sensorManager.startListening()
        syncManager.checkConnection()
        syncManager.requestStateRefresh()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.stopListening()
    }
}
