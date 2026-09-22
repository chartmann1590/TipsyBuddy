package com.hartmann.crosspromo.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import coil.load
import com.hartmann.crosspromo.HartmannCrossPromo
import com.hartmann.crosspromo.R
import com.hartmann.crosspromo.launcher.PlayStoreLauncher
import com.hartmann.crosspromo.model.PromoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android View implementation for XML layouts — no Compose migration needed:
 *
 *     <com.hartmann.crosspromo.ui.HartmannPromoView
 *         android:id="@+id/crossPromo"
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content"
 *         app:hcp_placement="settings"
 *         app:hcp_limit="3" />
 *
 * and optionally from code: `findViewById<HartmannPromoView>(...).load("home", 3)`.
 *
 * Behavior mirrors the Compose row: labeled section, horizontal cards,
 * silent failure (GONE when there is nothing to show), one impression per
 * card per response, TalkBack content descriptions. Built only on framework
 * widgets + Coil so XML hosts gain no new UI-framework dependency.
 */
class HartmannPromoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private var placement: String = "settings"
    private var limit: Int = 3
    private var loaded = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tracker by lazy { HartmannCrossPromo.impressionTracker() }

    init {
        visibility = GONE
        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.HartmannPromoView)
            placement = a.getString(R.styleable.HartmannPromoView_hcp_placement) ?: "settings"
            limit = a.getInt(R.styleable.HartmannPromoView_hcp_limit, 3).coerceIn(1, 6)
            a.recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!loaded) {
            loaded = true
            load(placement, limit)
        }
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }

    fun load(placement: String, limit: Int = 3) {
        this.placement = placement.ifBlank { "settings" }
        this.limit = limit.coerceIn(1, 6)
        if (!HartmannCrossPromo.isInitialized) {
            visibility = GONE
            return
        }
        val holder = HartmannCrossPromo.requireHolder()
        scope.launch {
            val apps = try {
                holder.repository.snapshot(this@HartmannPromoView.placement, this@HartmannPromoView.limit)
            } catch (_: Exception) {
                emptyList()
            }
            if (apps.isEmpty()) {
                visibility = GONE
                return@launch
            }
            render(apps)
        }
    }

    private fun render(apps: List<PromoApp>) {
        val holder = HartmannCrossPromo.requireHolder()
        removeAllViews()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        root.addView(TextView(context).apply {
            text = context.getString(R.string.hcp_more_from)
            setTextAppearance(android.R.style.TextAppearance_Material_Title)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        })

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        val appsShown = apps.take(limit)
        // Response-scoped dedupe key (stable for this rendered list).
        val responseKey = appsShown.joinToString("|") { it.packageName }.hashCode().toString()
        appsShown.forEachIndexed { index, app ->
            row.addView(buildCard(app, index))
            val key = tracker.key(responseKey, app.packageName, placement)
            if (tracker.shouldTrack(key)) {
                runCatching {
                    holder.analytics.impression(
                        sourcePackage = holder.sourcePackage,
                        targetPackage = app.packageName,
                        placement = placement,
                        rankPosition = index + 1,
                        selectionType = app.selectionType,
                        sessionId = holder.processSessionId,
                        recommendationRequestId = null,
                        sdkVersion = HartmannCrossPromo.SDK_VERSION,
                    )
                }
            }
        }
        root.addView(
            HorizontalScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                isHorizontalScrollBarEnabled = false
                addView(row)
            },
        )
        addView(root)
        visibility = VISIBLE
    }

    private fun buildCard(app: PromoApp, rankPosition: Int): View {
        val density = resources.displayMetrics.density
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                (260 * density).toInt(),
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                val m = (6 * density).toInt()
                setMargins(m, m, m, m)
            }
            background = roundedBackground(resolveThemeColor(android.R.attr.colorBackgroundFloating))
            val p = (12 * density).toInt()
            setPadding(p)
            isClickable = true
            isFocusable = true
            contentDescription =
                "${app.name}. ${app.shortDescription ?: "App by Hartmann Studios"}. View app on Google Play."
        }
        card.addView(ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((56 * density).toInt(), (56 * density).toInt())
            contentDescription = null // the card itself carries the description
            if (!app.iconUrl.isNullOrBlank()) {
                load(app.iconUrl) { crossfade(true) }
            } else {
                setImageResource(R.drawable.hcp_default_app_icon)
            }
        })

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (12 * density).toInt()
            }
        }
        textCol.addView(TextView(context).apply {
            text = app.name.ifBlank { app.packageName }
            setTextAppearance(android.R.style.TextAppearance_Material_Subhead)
            setTextColor(resolveThemeColor(android.R.attr.textColorPrimary))
            maxLines = 1
        })
        if (!app.shortDescription.isNullOrBlank()) {
            textCol.addView(TextView(context).apply {
                text = app.shortDescription
                setTextAppearance(android.R.style.TextAppearance_Material_Small)
                setTextColor(resolveThemeColor(android.R.attr.textColorSecondary))
                maxLines = 2
            })
        }
        app.rating?.let { rating ->
            textCol.addView(TextView(context).apply {
                text = context.getString(R.string.hcp_rating, rating)
                setTextAppearance(android.R.style.TextAppearance_Material_Caption)
                setTextColor(resolveThemeColor(android.R.attr.textColorSecondary))
            })
        }
        textCol.addView(Button(context, null, android.R.attr.borderlessButtonStyle).apply {
            text = context.getString(R.string.hcp_view_app)
            setOnClickListener { openApp(app, rankPosition) }
        })
        card.addView(textCol)
        card.setOnClickListener { openApp(app, rankPosition) }
        return card
    }

    private fun openApp(app: PromoApp, rankPosition: Int) {
        val holder = HartmannCrossPromo.requireHolder()
        runCatching {
            holder.analytics.click(
                sourcePackage = holder.sourcePackage,
                targetPackage = app.packageName,
                placement = placement,
                rankPosition = rankPosition,
                selectionType = app.selectionType,
                sessionId = holder.processSessionId,
                recommendationRequestId = null,
                sdkVersion = HartmannCrossPromo.SDK_VERSION,
            )
        }
        // Intent resolution is cheap; the launcher never does I/O and never
        // throws. The Play Store app (or browser fallback) handles the rest.
        runCatching {
            PlayStoreLauncher.launch(context.applicationContext, app.packageName, holder.sourcePackage)
        }
    }

    private fun roundedBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = 16 * resources.displayMetrics.density
        }
    }

    private fun resolveThemeColor(attr: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) tv.data else 0xFFCCCCCC.toInt()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
