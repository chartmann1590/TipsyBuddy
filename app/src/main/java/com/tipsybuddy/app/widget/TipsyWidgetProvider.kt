package com.tipsybuddy.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import com.tipsybuddy.app.MainActivity
import com.tipsybuddy.app.R
import com.tipsybuddy.app.data.AppDatabase
import com.tipsybuddy.app.data.DrinkEntity
import com.tipsybuddy.app.data.UserPreferences
import com.tipsybuddy.app.domain.BacCalculator
import com.tipsybuddy.app.domain.BacZone
import com.tipsybuddy.app.wear.PhoneWearSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TipsyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val quickAdd = QUICK_ADD_ACTIONS[action]
        if (quickAdd != null) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    insertQuickDrink(context, quickAdd)
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        if (action == ACTION_UPDATE_WIDGET) {
            updateAllWidgets(context)
            return
        }

        super.onReceive(context, intent)
    }

    companion object {
        private const val TAG = "TipsyWidget"

        const val ACTION_ADD_BEER = "com.tipsybuddy.app.ACTION_ADD_BEER"
        const val ACTION_ADD_WINE = "com.tipsybuddy.app.ACTION_ADD_WINE"
        const val ACTION_ADD_SHOT = "com.tipsybuddy.app.ACTION_ADD_SHOT"
        const val ACTION_ADD_WATER = "com.tipsybuddy.app.ACTION_ADD_WATER"
        const val ACTION_UPDATE_WIDGET = "com.tipsybuddy.app.ACTION_UPDATE_WIDGET"

        private data class QuickAdd(
            val name: String,
            val category: String,
            val volumeOz: Double,
            val abv: Double,
            val price: Double,
            val toastText: String
        )

        private val QUICK_ADD_ACTIONS = mapOf(
            ACTION_ADD_BEER to QuickAdd("Draft Beer", "Beer", 12.0, 5.0, 7.0, "🍺 Beer logged from widget"),
            ACTION_ADD_WINE to QuickAdd("Wine Glass", "Wine", 5.0, 12.5, 11.0, "🍷 Wine logged from widget"),
            ACTION_ADD_SHOT to QuickAdd("Spirits Shot", "Shot", 1.5, 40.0, 8.0, "🥃 Shot logged from widget"),
            ACTION_ADD_WATER to QuickAdd("Pint of Water", "Water", 16.0, 0.0, 0.0, "💧 Water logged from widget")
        )

        private suspend fun insertQuickDrink(context: Context, drink: QuickAdd) {
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val db = AppDatabase.getInstance(context)
            db.drinkDao().insertDrink(
                DrinkEntity(
                    name = drink.name,
                    category = drink.category,
                    volumeOz = drink.volumeOz,
                    abv = drink.abv,
                    price = drink.price,
                    timestamp = System.currentTimeMillis(),
                    sessionDate = todayStr
                )
            )
            Log.d(TAG, "Logged ${drink.name} from widget")
            try {
                PhoneWearSyncManager.getInstance(context).pushLatestStateToWatch()
            } catch (t: Throwable) {
                Log.w(TAG, "Watch sync after widget log failed", t)
            }
            updateAllWidgets(context)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, drink.toastText, Toast.LENGTH_SHORT).show()
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val views = buildRemoteViews(context)
                    Handler(Looper.getMainLooper()).post {
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to update widget $appWidgetId", t)
                }
            }
        }

        private suspend fun buildRemoteViews(context: Context): RemoteViews {
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
            views.setTextViewText(R.id.widget_bac_text, String.format(Locale.US, "%.3f%%", bacResult.bac))
            views.setTextViewText(R.id.widget_zone_badge, bacResult.zone.title.uppercase(Locale.US))

            val zoneColor = when (bacResult.zone) {
                BacZone.SOBER -> Color.parseColor("#10B981")
                BacZone.BUZZED -> Color.parseColor("#38BDF8")
                BacZone.TIPSY -> Color.parseColor("#F59E0B")
                BacZone.INTOXICATED -> Color.parseColor("#EF4444")
                BacZone.HIGH_RISK -> Color.parseColor("#DC2626")
            }
            views.setTextColor(R.id.widget_zone_badge, zoneColor)
            views.setTextColor(
                R.id.widget_bac_text,
                if (bacResult.bac == 0.0) Color.parseColor("#10B981") else zoneColor
            )

            val statusText = if (bacResult.alcoholCount == 0 && bacResult.waterCount == 0) {
                context.getString(R.string.widget_status_empty)
            } else {
                "${bacResult.alcoholCount} drinks • 💧 ${bacResult.waterCount} waters • ${bacResult.zone.title}"
            }
            views.setTextViewText(R.id.widget_status_text, statusText)
            views.setTextViewText(
                R.id.widget_detail_text,
                buildDetailLine(bacResult.totalCost, bacResult.hydrationScorePercent, bacResult.hoursToSober)
            )

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_open_app, openAppPendingIntent)

            views.setOnClickPendingIntent(R.id.widget_btn_beer, broadcastPendingIntent(context, ACTION_ADD_BEER, 1))
            views.setOnClickPendingIntent(R.id.widget_btn_wine, broadcastPendingIntent(context, ACTION_ADD_WINE, 2))
            views.setOnClickPendingIntent(R.id.widget_btn_shot, broadcastPendingIntent(context, ACTION_ADD_SHOT, 3))
            views.setOnClickPendingIntent(R.id.widget_btn_water, broadcastPendingIntent(context, ACTION_ADD_WATER, 4))
            return views
        }

        private fun broadcastPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, TipsyWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildDetailLine(totalCost: Double, hydration: Int, hoursToSober: Double): String {
            val costStr = String.format(Locale.US, "$%.0f tab", totalCost)
            val hydroStr = "$hydration% hydrated"
            val soberStr = if (hoursToSober <= 0.0) {
                "Sober now"
            } else {
                String.format(Locale.US, "Sober in %.1fh", hoursToSober)
            }
            return "$costStr • $hydroStr • $soberStr"
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
