package com.petcompanion.data.local

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * จัดการ Google Play Billing สำหรับ Teacher Subscription
 * Products ที่ต้องสร้างใน Play Console:
 *   - teacher_monthly  (Subscription, ราคาที่ตั้ง = 99 THB/เดือน)
 *   - teacher_yearly   (Subscription, ราคาที่ตั้ง = 799 THB/ปี)
 */
class BillingManager(context: Context) {

    companion object {
        const val PRODUCT_MONTHLY = "teacher_monthly"
        const val PRODUCT_YEARLY  = "teacher_yearly"
    }

    // ── Dev override ───────────────────────────────────────────────────────
    private val devPrefs = context.getSharedPreferences("dev_prefs", Context.MODE_PRIVATE)
    private val _isDevOverride = MutableStateFlow(devPrefs.getBoolean("pro_override", false))
    val isDevOverrideFlow: StateFlow<Boolean> = _isDevOverride.asStateFlow()
    val isDevOverride: Boolean get() = _isDevOverride.value

    fun setDevProOverride(enabled: Boolean) {
        devPrefs.edit().putBoolean("pro_override", enabled).apply()
        _isDevOverride.value = enabled
        _isPremium.value     = enabled
    }

    // ── Subscription state ─────────────────────────────────────────────────
    private val _isPremium = MutableStateFlow(isDevOverride)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    // ── Active product details (for displaying price) ──────────────────────
    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetails: StateFlow<List<ProductDetails>> = _productDetails.asStateFlow()

    // ── BillingClient ──────────────────────────────────────────────────────
    private val billingClient = BillingClient.newBuilder(context)
        .setListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                handlePurchases(purchases)
            }
        }
        .enablePendingPurchases()
        .build()

    init {
        if (isDevOverride) _isPremium.value = true
        connectAndQuery()
    }

    private fun connectAndQuery() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryExistingSubscriptions()
                    queryProductDetails()
                }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    // ── ตรวจสอบ subscription ที่มีอยู่แล้ว ─────────────────────────────────
    private fun queryExistingSubscriptions() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                handlePurchases(purchases)
            }
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val hasActive = purchases.any { purchase ->
            (purchase.products.contains(PRODUCT_MONTHLY) ||
             purchase.products.contains(PRODUCT_YEARLY)) &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (!isDevOverride) _isPremium.value = hasActive

        // Acknowledge purchases ที่ยังไม่ได้ acknowledge
        purchases.filter { !it.isAcknowledged && it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .forEach { purchase ->
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken).build()
                billingClient.acknowledgePurchase(params) {}
            }
    }

    // ── ดึงข้อมูลสินค้า (ราคา, ชื่อ) จาก Play Store ────────────────────────
    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder().setProductList(productList).build()
        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = details
            }
        }
    }

    // ── เปิดหน้า Billing Flow (เรียกจาก Activity) ─────────────────────────
    fun launchBillingFlow(activity: Activity, productId: String): Boolean {
        val details = _productDetails.value.find { it.productId == productId } ?: return false
        val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return false

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        billingClient.launchBillingFlow(activity, flowParams)
        return true
    }

    fun getFormattedPrice(productId: String): String {
        val details = _productDetails.value.find { it.productId == productId } ?: return ""
        return details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice ?: ""
    }
}
