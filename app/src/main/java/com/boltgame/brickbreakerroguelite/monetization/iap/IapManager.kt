package com.boltgame.brickbreakerroguelite.monetization.iap

import android.content.Context
import android.content.SharedPreferences
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.boltgame.brickbreakerroguelite.data.repository.GameProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min

interface IapManager {
    val iapState: StateFlow<IapState>
    fun initialize()
    fun queryProducts()
    fun launchBillingFlow(productId: String)
    fun onPurchaseAction(action: PurchaseAction)
    fun getOptimalOffer(userSpendingTier: SpendingTier): ProductDetails?
}

data class IapState(
    val isConnected: Boolean = false,
    val products: List<ProductDetails> = emptyList(),
    val pendingPurchase: Boolean = false,
    val purchaseAnalytics: PurchaseAnalytics = PurchaseAnalytics(),
    val dynamicOffers: List<DynamicOffer> = emptyList()
)

data class PurchaseAnalytics(
    val totalPurchases: Int = 0,
    val totalRevenue: Float = 0f,
    val averagePurchaseValue: Float = 0f,
    val lastPurchaseTime: Long = 0,
    val conversionEvents: List<ConversionEvent> = emptyList()
)

data class ConversionEvent(
    val action: PurchaseAction,
    val timestamp: Long,
    val productViewed: String?,
    val purchaseCompleted: Boolean
)

data class DynamicOffer(
    val productId: String,
    val originalPrice: Float,
    val discountedPrice: Float,
    val discountPercentage: Int,
    val expirationTime: Long,
    val triggerCondition: String
)

enum class PurchaseAction {
    VIEW_STORE,
    VIEW_PRODUCT,
    INITIATE_PURCHASE,
    COMPLETE_PURCHASE,
    CANCEL_PURCHASE,
    GAME_OVER_NO_CURRENCY,
    UPGRADE_BLOCKED,
    SPECIAL_OFFER_TRIGGER
}

enum class SpendingTier {
    FREE_USER,      // No purchases
    LOW_SPENDER,    // $0.99 - $4.99
    MID_SPENDER,    // $5.00 - $19.99
    HIGH_SPENDER,   // $20.00+
    WHALE          // $50.00+
}

class IapManagerImpl(
    private val context: Context,
    private val gameProgressRepository: GameProgressRepository
) : IapManager, PurchasesUpdatedListener {
    
    private val _iapState = MutableStateFlow(IapState())
    override val iapState: StateFlow<IapState> = _iapState.asStateFlow()
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("iap_prefs", Context.MODE_PRIVATE)
    
    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()
    
    // Enhanced product catalog with value propositions
    private val productCatalog = mapOf(
        "starter_pack" to PurchaseBundle(99, 150, "Limited Time!"),
        "soft_currency_small" to PurchaseBundle(99, 100, "Best for Beginners"),
        "soft_currency_medium" to PurchaseBundle(299, 350, "Most Popular!"),
        "soft_currency_large" to PurchaseBundle(499, 650, "Best Value!"),
        "premium_upgrade" to PurchaseBundle(999, 1500, "VIP Access"),
        "remove_ads" to PurchaseBundle(199, 0, "Ad-Free Experience"),
        "double_rewards" to PurchaseBundle(149, 0, "2X Rewards Forever"),
        "power_pack" to PurchaseBundle(399, 500, "Instant Power-Up Pack")
    )
    
    // Dynamic pricing for different markets
    private val inAppProductIds = listOf(
        "starter_pack",
        "soft_currency_small",
        "soft_currency_medium", 
        "soft_currency_large",
        "premium_upgrade",
        "remove_ads",
        "double_rewards",
        "power_pack"
    )
    
    init {
        loadPurchaseAnalytics()
        generateDynamicOffers()
    }
    
    override fun initialize() {
        if (_iapState.value.isConnected) return
        
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    _iapState.value = _iapState.value.copy(isConnected = true)
                    queryProducts()
                    processPendingPurchases()
                    
                    // Track successful billing setup
                    trackConversionEvent(PurchaseAction.VIEW_STORE, null, false)
                }
            }
            
            override fun onBillingServiceDisconnected() {
                _iapState.value = _iapState.value.copy(isConnected = false)
                // Implement exponential backoff retry
                scheduleReconnection()
            }
        })
    }
    
    override fun queryProducts() {
        if (!_iapState.value.isConnected) {
            initialize()
            return
        }
        
        coroutineScope.launch {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    inAppProductIds.map { productId ->
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(productId)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    }
                )
                .build()
            
            val productDetailsResult = billingClient.queryProductDetails(params)
            
            if (productDetailsResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val products = productDetailsResult.productDetailsList ?: emptyList()
                _iapState.value = _iapState.value.copy(products = products)
                
                // Update dynamic offers based on product details
                updateDynamicOffers(products)
            }
        }
    }
    
    override fun launchBillingFlow(productId: String) {
        if (!_iapState.value.isConnected) {
            initialize()
            return
        }
        
        // Track purchase initiation
        trackConversionEvent(PurchaseAction.INITIATE_PURCHASE, productId, false)
        
        val productDetails = _iapState.value.products.find { it.productId == productId } ?: return
        
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )
        
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
        
        _iapState.value = _iapState.value.copy(pendingPurchase = true)
        
        val result = billingClient.launchBillingFlow(context as android.app.Activity, billingFlowParams)
        
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _iapState.value = _iapState.value.copy(pendingPurchase = false)
            trackConversionEvent(PurchaseAction.CANCEL_PURCHASE, productId, false)
        }
    }
    
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.let { purchaseList ->
                    coroutineScope.launch {
                        for (purchase in purchaseList) {
                            handlePurchase(purchase)
                        }
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                trackConversionEvent(PurchaseAction.CANCEL_PURCHASE, null, false)
            }
            else -> {
                // Handle other error cases
            }
        }
        
        _iapState.value = _iapState.value.copy(pendingPurchase = false)
    }
    
    override fun onPurchaseAction(action: PurchaseAction) {
        when (action) {
            PurchaseAction.VIEW_STORE -> {
                trackConversionEvent(action, null, false)
                generatePersonalizedOffers()
            }
            PurchaseAction.VIEW_PRODUCT -> {
                trackConversionEvent(action, null, false)
            }
            PurchaseAction.GAME_OVER_NO_CURRENCY -> {
                trackConversionEvent(action, null, false)
                triggerEmergencyOffer()
            }
            PurchaseAction.UPGRADE_BLOCKED -> {
                trackConversionEvent(action, null, false)
                suggestValueOffer()
            }
            else -> {
                trackConversionEvent(action, null, false)
            }
        }
    }
    
    override fun getOptimalOffer(userSpendingTier: SpendingTier): ProductDetails? {
        val products = _iapState.value.products
        
        return when (userSpendingTier) {
            SpendingTier.FREE_USER -> {
                // Show low-barrier entry offers
                products.find { it.productId == "starter_pack" } 
                    ?: products.find { it.productId == "soft_currency_small" }
            }
            SpendingTier.LOW_SPENDER -> {
                // Show mid-tier value offers
                products.find { it.productId == "soft_currency_medium" }
                    ?: products.find { it.productId == "remove_ads" }
            }
            SpendingTier.MID_SPENDER -> {
                // Show premium offers
                products.find { it.productId == "soft_currency_large" }
                    ?: products.find { it.productId == "premium_upgrade" }
            }
            SpendingTier.HIGH_SPENDER, SpendingTier.WHALE -> {
                // Show highest value offers
                products.find { it.productId == "premium_upgrade" }
                    ?: products.find { it.productId == "power_pack" }
            }
        }
    }
    
    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            val productId = purchase.products.firstOrNull() ?: return
            val bundle = productCatalog[productId]
            
            // Grant entitlement based on product ID
            when (productId) {
                "starter_pack" -> {
                    grantCurrency(150)
                    unlockStarterBonuses()
                }
                "soft_currency_small" -> grantCurrency(100)
                "soft_currency_medium" -> grantCurrency(350)
                "soft_currency_large" -> grantCurrency(650)
                "premium_upgrade" -> {
                    grantCurrency(1500)
                    unlockPremiumFeatures()
                }
                "remove_ads" -> setRemoveAds(true)
                "double_rewards" -> setDoubleRewards(true)
                "power_pack" -> {
                    grantCurrency(500)
                    grantPowerUpPack()
                }
            }
            
            // Update analytics
            updatePurchaseAnalytics(bundle?.priceInCents ?: 0, productId)
            trackConversionEvent(PurchaseAction.COMPLETE_PURCHASE, productId, true)
            
            // Acknowledge purchase if not already acknowledged
            if (!purchase.isAcknowledged) {
                val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                
                billingClient.acknowledgePurchase(acknowledgePurchaseParams)
            }
            
            // Generate follow-up offers
            generateFollowUpOffers(productId)
        }
    }
    
    private fun generateDynamicOffers() {
        val currentTime = System.currentTimeMillis()
        val analytics = _iapState.value.purchaseAnalytics
        val offers = mutableListOf<DynamicOffer>()
        
        // First-time buyer offer
        if (analytics.totalPurchases == 0) {
            offers.add(
                DynamicOffer(
                    productId = "starter_pack",
                    originalPrice = 0.99f,
                    discountedPrice = 0.49f,
                    discountPercentage = 50,
                    expirationTime = currentTime + 24 * 60 * 60 * 1000, // 24 hours
                    triggerCondition = "first_time_buyer"
                )
            )
        }
        
        // Re-engagement offer
        if (currentTime - analytics.lastPurchaseTime > 7 * 24 * 60 * 60 * 1000) { // 7 days
            offers.add(
                DynamicOffer(
                    productId = "soft_currency_medium",
                    originalPrice = 2.99f,
                    discountedPrice = 1.99f,
                    discountPercentage = 33,
                    expirationTime = currentTime + 3 * 24 * 60 * 60 * 1000, // 3 days
                    triggerCondition = "win_back"
                )
            )
        }
        
        _iapState.value = _iapState.value.copy(dynamicOffers = offers)
    }
    
    private fun updateDynamicOffers(products: List<ProductDetails>) {
        // Update offers with real pricing data from store
        // This would typically involve A/B testing different price points
        generateDynamicOffers()
    }
    
    private fun generatePersonalizedOffers() {
        val spendingTier = calculateSpendingTier()
        val optimalOffer = getOptimalOffer(spendingTier)
        
        // Show personalized offers in UI
        optimalOffer?.let { offer ->
            // Trigger UI to show the offer
        }
    }
    
    private fun triggerEmergencyOffer() {
        // Show immediate value offer when user runs out of currency
        val offer = _iapState.value.products.find { it.productId == "soft_currency_small" }
        offer?.let {
            // Show emergency purchase dialog
        }
    }
    
    private fun suggestValueOffer() {
        // Suggest offers that unlock progression when blocked
        val offer = _iapState.value.products.find { it.productId == "premium_upgrade" }
        offer?.let {
            // Show value proposition dialog
        }
    }
    
    private fun calculateSpendingTier(): SpendingTier {
        val totalRevenue = _iapState.value.purchaseAnalytics.totalRevenue
        return when {
            totalRevenue == 0f -> SpendingTier.FREE_USER
            totalRevenue < 5f -> SpendingTier.LOW_SPENDER
            totalRevenue < 20f -> SpendingTier.MID_SPENDER
            totalRevenue < 50f -> SpendingTier.HIGH_SPENDER
            else -> SpendingTier.WHALE
        }
    }
    
    private fun generateFollowUpOffers(lastPurchasedProductId: String) {
        // Generate complementary offers based on last purchase
        when (lastPurchasedProductId) {
            "soft_currency_small" -> {
                // Suggest upgrade to medium pack
            }
            "remove_ads" -> {
                // Suggest double rewards
            }
            "starter_pack" -> {
                // Suggest premium upgrade
            }
        }
    }
    
    private fun updatePurchaseAnalytics(priceInCents: Int, productId: String) {
        val currentAnalytics = _iapState.value.purchaseAnalytics
        val priceInDollars = priceInCents / 100f
        
        val updatedAnalytics = currentAnalytics.copy(
            totalPurchases = currentAnalytics.totalPurchases + 1,
            totalRevenue = currentAnalytics.totalRevenue + priceInDollars,
            averagePurchaseValue = (currentAnalytics.totalRevenue + priceInDollars) / (currentAnalytics.totalPurchases + 1),
            lastPurchaseTime = System.currentTimeMillis()
        )
        
        _iapState.value = _iapState.value.copy(purchaseAnalytics = updatedAnalytics)
        savePurchaseAnalytics()
    }
    
    private fun trackConversionEvent(action: PurchaseAction, productId: String?, completed: Boolean) {
        val event = ConversionEvent(
            action = action,
            timestamp = System.currentTimeMillis(),
            productViewed = productId,
            purchaseCompleted = completed
        )
        
        val currentAnalytics = _iapState.value.purchaseAnalytics
        val updatedEvents = (currentAnalytics.conversionEvents + event).takeLast(50) // Keep last 50 events
        
        _iapState.value = _iapState.value.copy(
            purchaseAnalytics = currentAnalytics.copy(conversionEvents = updatedEvents)
        )
        
        savePurchaseAnalytics()
    }
    
    private fun grantCurrency(amount: Int) {
        coroutineScope.launch {
            gameProgressRepository.addSoftCurrency(amount)
        }
    }
    
    private fun setRemoveAds(removeAds: Boolean) {
        sharedPrefs.edit().putBoolean("remove_ads", removeAds).apply()
    }
    
    private fun setDoubleRewards(doubleRewards: Boolean) {
        sharedPrefs.edit().putBoolean("double_rewards", doubleRewards).apply()
    }
    
    private fun unlockPremiumFeatures() {
        sharedPrefs.edit().putBoolean("premium_user", true).apply()
        coroutineScope.launch {
            gameProgressRepository.unlockPremiumFeatures()
        }
    }
    
    private fun unlockStarterBonuses() {
        coroutineScope.launch {
            gameProgressRepository.unlockStarterPack()
        }
    }
    
    private fun grantPowerUpPack() {
        coroutineScope.launch {
            gameProgressRepository.grantPowerUpPack()
        }
    }
    
    private fun scheduleReconnection() {
        coroutineScope.launch {
            kotlinx.coroutines.delay(5000) // Retry after 5 seconds
            initialize()
        }
    }
    
    private fun loadPurchaseAnalytics() {
        val analytics = PurchaseAnalytics(
            totalPurchases = sharedPrefs.getInt("total_purchases", 0),
            totalRevenue = sharedPrefs.getFloat("total_revenue", 0f),
            averagePurchaseValue = sharedPrefs.getFloat("avg_purchase_value", 0f),
            lastPurchaseTime = sharedPrefs.getLong("last_purchase_time", 0)
        )
        
        _iapState.value = _iapState.value.copy(purchaseAnalytics = analytics)
    }
    
    private fun savePurchaseAnalytics() {
        val analytics = _iapState.value.purchaseAnalytics
        sharedPrefs.edit()
            .putInt("total_purchases", analytics.totalPurchases)
            .putFloat("total_revenue", analytics.totalRevenue)
            .putFloat("avg_purchase_value", analytics.averagePurchaseValue)
            .putLong("last_purchase_time", analytics.lastPurchaseTime)
            .apply()
    }
    
    private fun processPendingPurchases() {
        coroutineScope.launch {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            
            val purchasesResult = billingClient.queryPurchasesAsync(params)
            
            if (purchasesResult.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                for (purchase in purchasesResult.purchasesList) {
                    handlePurchase(purchase)
                }
            }
        }
    }
    
    private data class PurchaseBundle(
        val priceInCents: Int,
        val currencyReward: Int,
        val valueProposition: String
    )
}