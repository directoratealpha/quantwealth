package com.directoratealpha.quantwealth.billing

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BillingManager(
    private val context: Context,
    private val onProStatusChanged: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_MONTHLY_SUB = "quantwealth_pro_monthly"
        const val PRODUCT_LIFETIME_INAPP = "quantwealth_pro_lifetime"
    }

    private val _monthlyPrice = MutableStateFlow("$1.99/mo")
    val monthlyPrice: StateFlow<String> = _monthlyPrice

    private val _lifetimePrice = MutableStateFlow("$9.99")
    val lifetimePrice: StateFlow<String> = _lifetimePrice

    private var monthlyProductDetails: ProductDetails? = null
    private var lifetimeProductDetails: ProductDetails? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .build()

    fun startConnection() {
        if (billingClient.isReady) {
            queryCatalogAndPurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryCatalogAndPurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Reconnects automatically on next purchase attempt
            }
        })
    }

    private fun queryCatalogAndPurchases() {
        querySubscriptionProduct()
        queryLifetimeProduct()
        restoreExistingPurchases()
    }

    private fun querySubscriptionProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_MONTHLY_SUB)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && list.isNotEmpty()) {
                val details = list.first()
                monthlyProductDetails = details
                val price = details.subscriptionOfferDetails
                    ?.firstOrNull()
                    ?.pricingPhases
                    ?.pricingPhaseList
                    ?.firstOrNull()
                    ?.formattedPrice
                if (!price.isNullOrBlank()) {
                    _monthlyPrice.value = "$price/mo"
                }
            }
        }
    }

    private fun queryLifetimeProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_LIFETIME_INAPP)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && list.isNotEmpty()) {
                val details = list.first()
                lifetimeProductDetails = details
                val price = details.oneTimePurchaseOfferDetails?.formattedPrice
                if (!price.isNullOrBlank()) {
                    _lifetimePrice.value = price
                }
            }
        }
    }

    fun restoreExistingPurchases() {
        if (!billingClient.isReady) return
        var activeSub = false
        var activeLifetime = false

        val subParams = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(subParams) { subRes, subPurchases ->
            if (subRes.responseCode == BillingClient.BillingResponseCode.OK) {
                for (p in subPurchases) {
                    if (p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                        p.products.contains(PRODUCT_MONTHLY_SUB)
                    ) {
                        activeSub = true
                        acknowledgeIfNeeded(p)
                    }
                }
            }
            val inAppParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            billingClient.queryPurchasesAsync(inAppParams) { inAppRes, inAppPurchases ->
                if (inAppRes.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (p in inAppPurchases) {
                        if (p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                            p.products.contains(PRODUCT_LIFETIME_INAPP)
                        ) {
                            activeLifetime = true
                            acknowledgeIfNeeded(p)
                        }
                    }
                }
                if (activeSub || activeLifetime) {
                    onProStatusChanged(true)
                }
            }
        }
    }

    fun launchMonthlyPurchase(activity: Activity) {
        val details = monthlyProductDetails
        val offerToken = details?.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (details == null || offerToken == null) {
            Toast.makeText(
                activity,
                "Play Store billing activates once uploaded to Google Play Console.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    fun launchLifetimePurchase(activity: Activity) {
        val details = lifetimeProductDetails
        if (details == null) {
            Toast.makeText(
                activity,
                "Play Store billing activates once uploaded to Google Play Console.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )
            )
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (p in purchases) {
                if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    acknowledgeIfNeeded(p)
                    if (p.products.contains(PRODUCT_MONTHLY_SUB) || p.products.contains(PRODUCT_LIFETIME_INAPP)) {
                        onProStatusChanged(true)
                    }
                }
            }
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) {}
        }
    }

    fun destroy() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }
}
