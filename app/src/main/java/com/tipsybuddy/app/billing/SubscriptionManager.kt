package com.tipsybuddy.app.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.tipsybuddy.app.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Handles Google Play In-App Subscription billing for TipsyBuddy Ad-Free.
 */
class SubscriptionManager private constructor(private val appContext: Context) :
    PurchasesUpdatedListener, BillingClientStateListener {

    companion object {
        private const val TAG = "TipsyBilling"
        const val SUBSCRIPTION_PRODUCT_ID = "tipsybuddy_ad_free_monthly"
        const val PLAY_STORE_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"

        @Volatile
        private var instance: SubscriptionManager? = null

        fun getInstance(context: Context): SubscriptionManager {
            return instance ?: synchronized(this) {
                instance ?: SubscriptionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val userPrefs = UserPreferences(appContext)
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isAdFree = MutableStateFlow(userPrefs.isAdFreeSubscribed)
    val isAdFree: StateFlow<Boolean> = _isAdFree.asStateFlow()

    private val _isBillingReady = MutableStateFlow(false)
    val isBillingReady: StateFlow<Boolean> = _isBillingReady.asStateFlow()

    private val _formattedPrice = MutableStateFlow("$1.99 / month")
    val formattedPrice: StateFlow<String> = _formattedPrice.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
        .enableOneTimeProducts()
        .build()

    private var billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(pendingPurchasesParams)
        .build()

    init {
        startConnection()
    }

    fun startConnection() {
        if (!billingClient.isReady) {
            billingClient.startConnection(this)
        }
    }

    override fun onBillingSetupFinished(billingResult: BillingResult) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            Log.d(TAG, "Google Play Billing setup successful")
            _isBillingReady.value = true
            querySubscriptionDetails()
            queryPurchases()
        } else {
            Log.w(TAG, "Billing setup failed: ${billingResult.responseCode} - ${billingResult.debugMessage}")
            _isBillingReady.value = false
        }
    }

    override fun onBillingServiceDisconnected() {
        Log.w(TAG, "Billing service disconnected")
        _isBillingReady.value = false
    }

    fun querySubscriptionDetails() {
        if (!billingClient.isReady) {
            startConnection()
            return
        }

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SUBSCRIPTION_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = queryResult.productDetailsList.firstOrNull { it.productId == SUBSCRIPTION_PRODUCT_ID }
                _productDetails.value = details
                details?.let { prod ->
                    val offer = prod.subscriptionOfferDetails?.firstOrNull()
                    val pricingPhase = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()
                    val price = pricingPhase?.formattedPrice
                    if (!price.isNullOrBlank()) {
                        _formattedPrice.value = "$price / month"
                    }
                }
                Log.d(TAG, "Fetched product details: ${_productDetails.value?.name}, price: ${_formattedPrice.value}")
            } else {
                Log.w(TAG, "queryProductDetailsAsync failed: ${billingResult.debugMessage}")
            }
        }
    }

    fun queryPurchases() {
        if (!billingClient.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var hasActiveSub = false
                for (purchase in purchases) {
                    if (purchase.products.contains(SUBSCRIPTION_PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    ) {
                        hasActiveSub = true
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    }
                }
                updateSubscriptionState(hasActiveSub)
                Log.d(TAG, "Active subscription status: $hasActiveSub")
            } else {
                Log.w(TAG, "queryPurchasesAsync error: ${billingResult.debugMessage}")
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity): Boolean {
        if (!billingClient.isReady) {
            startConnection()
            return false
        }

        val details = _productDetails.value
        if (details == null) {
            querySubscriptionDetails()
            return false
        }

        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: ""
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .apply {
                    if (offerToken.isNotEmpty()) {
                        setOfferToken(offerToken)
                    }
                }
                .build()
        )

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        val response = billingClient.launchBillingFlow(activity, flowParams)
        return response.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun restorePurchases(onComplete: (Boolean) -> Unit) {
        if (!billingClient.isReady) {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _isBillingReady.value = true
                        queryPurchasesWithCallback(onComplete)
                    } else {
                        onComplete(false)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    onComplete(false)
                }
            })
            return
        }
        queryPurchasesWithCallback(onComplete)
    }

    private fun queryPurchasesWithCallback(onComplete: (Boolean) -> Unit) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                var foundActive = false
                for (purchase in purchases) {
                    if (purchase.products.contains(SUBSCRIPTION_PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    ) {
                        foundActive = true
                        if (!purchase.isAcknowledged) {
                            acknowledgePurchase(purchase)
                        }
                    }
                }
                updateSubscriptionState(foundActive)
                scope.launch { onComplete(foundActive) }
            } else {
                scope.launch { onComplete(false) }
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled subscription flow")
        } else {
            Log.w(TAG, "onPurchasesUpdated code: ${billingResult.responseCode} - ${billingResult.debugMessage}")
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (purchase.products.contains(SUBSCRIPTION_PRODUCT_ID)) {
                updateSubscriptionState(true)
            }
            if (!purchase.isAcknowledged) {
                acknowledgePurchase(purchase)
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        val ackParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(ackParams) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Subscription purchase acknowledged successfully")
            } else {
                Log.w(TAG, "Failed to acknowledge subscription: ${result.debugMessage}")
            }
        }
    }

    private fun updateSubscriptionState(active: Boolean) {
        _isAdFree.value = active
        userPrefs.isAdFreeSubscribed = active
    }

    fun openPlayStoreSubscriptions(activity: Activity) {
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("$PLAY_STORE_SUBSCRIPTIONS_URL?package=${appContext.packageName}&sku=$SUBSCRIPTION_PRODUCT_ID")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        } catch (_: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(PLAY_STORE_SUBSCRIPTIONS_URL)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(webIntent)
        }
    }
}
