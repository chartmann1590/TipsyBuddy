package com.tipsybuddy.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.Toast
import com.tipsybuddy.app.MainActivity
import com.tipsybuddy.app.R
import com.tipsybuddy.app.data.AppDatabase
import com.tipsybuddy.app.data.DrinkEntity
import com.tipsybuddy.app.data.UserPreferences
import com.tipsybuddy.app.domain.BacCalculator
import com.tipsybuddy.app.domain.BacZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TipsyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        when (action) {
            ACTION_ADD_BEER -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getInstance(context)
                    db.drinkDao().insertDrink(
                        DrinkEntity(
                            name = "Draft Beer",
                            category = "Domestic Beer",
                            volumeOz = 12.0,
                            abv = 5.0,
                            price = 6.0,
                            timestamp = System.currentTimeMillis(),
                            sessionDate = todayStr
                        )
                    )
                    updateAllWidgets(context)
                }
                Toast.makeText(context, "🍺 Beer logged via Home Screen Widget!", Toast.LENGTH_SHORT).show()
            }

            ACTION_ADD_WATER -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getInstance(context)
                    db.drinkDao().insertDrink(
                        DrinkEntity(
                            name = "Pint of Water",
                            category = "Water",
                            volumeOz = 16.0,
                            abv = 0.0,
                            price = 0.0,
                            timestamp = System.currentTimeMillis(),
                            sessionDate = todayStr
                        )
                    )
                    updateAllWidgets(context)
                }
                Toast.makeText(context, "💧 Water logged! Hydration up!", Toast.LENGTH_SHORT).show()
            }

            ACTION_UPDATE_WIDGET -> {
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_ADD_BEER = "com.tipsybuddy.app.ACTION_ADD_BEER"
        const val ACTION_ADD_WATER = "com.tipsybuddy.app.ACTION_ADD_WATER"
        const val ACTION_UPDATE_WIDGET = "com.tipsybuddy.app.ACTION_UPDATE_WIDGET"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(context)
                val userPrefs = UserPreferences(context)
                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

                val drinks = db.drinkDao().getDrinksForDateDirect(todayStr)
                val bacResult = BacCalculator.calculate(
                    drinks = drinks,
                    weightLbs = userPrefs.weightLbs,
                    gender = userPrefs.gender
                )

                val views = RemoteViews(context.packageName, R.layout.tipsy_widget)

                // BAC Text
                views.setTextViewText(R.id.widget_bac_text, String.format(Locale.US, "%.3f%%", bacResult.bac))

                // Zone Badge
                views.setTextViewText(R.id.widget_zone_badge, bacResult.zone.title.uppercase())
                val zoneColor = when (bacResult.zone) {
                    BacZone.SOBER -> Color.parseColor("#10B981")
                    BacZone.BUZZED -> Color.parseColor("#38BDF8")
                    BacZone.TIPSY -> Color.parseColor("#F59E0B")
                    BacZone.INTOXICATED -> Color.parseColor("#EF4444")
                    BacZone.HIGH_RISK -> Color.parseColor("#DC2626")
                }
                views.setTextColor(R.id.widget_zone_badge, zoneColor)
                views.setTextColor(R.id.widget_bac_text, if (bacResult.bac == 0.0) Color.parseColor("#10B981") else zoneColor)

                // Subtitle: drink counts & advice
                val alcoholCount = drinks.count { it.isAlcoholic }
                val statusText = if (alcoholCount == 0) {
                    "0 drinks tonight • Reflexes clear"
                } else {
                    "$alcoholCount drinks • 💧 ${bacResult.waterCount} waters"
                }
                views.setTextViewText(R.id.widget_status_text, statusText)

                // PendingIntent to launch MainActivity on container tap
                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openAppPendingIntent = PendingIntent.getActivity(
                    context, 0, openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

                // PendingIntent to add Beer
                val addBeerIntent = Intent(context, TipsyWidgetProvider::class.java).apply {
                    action = ACTION_ADD_BEER
                }
                val addBeerPendingIntent = PendingIntent.getBroadcast(
                    context, 1, addBeerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_beer, addBeerPendingIntent)

                // PendingIntent to add Water
                val addWaterIntent = Intent(context, TipsyWidgetProvider::class.java).apply {
                    action = ACTION_ADD_WATER
                }
                val addWaterPendingIntent = PendingIntent.getBroadcast(
                    context, 2, addWaterIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_water, addWaterPendingIntent)

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, TipsyWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (widgetId in allWidgetIds) {
                updateAppWidget(context, appWidgetManager, widgetId)
            }
        }
    }
}
