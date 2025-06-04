package com.boltgame.brickbreakerroguelite.monetization.ads

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min

interface AdManager {
    val adState: StateFlow<AdState>
    fun loadRewardedAd()
    fun loadInterstitialAd()
    fun showRewardedAd(onRewarded: (Int) -> Unit, onDismissed: () -> Unit)
    fun showInterstitialAd(onDismissed: () -> Unit)
    fun shouldShowInterstitialAd(): Boolean
    fun onGameAction(action: AdTriggerAction)
}

data class AdState(
    val isRewardedAdLoaded: Boolean = false,
    val isInterstitialAdLoaded: Boolean = false,
    val isAdShowing: Boolean = false,
    val isLoading: Boolean = false,
    val adFrequencyData: AdFrequencyData = AdFrequencyData()
)

data class AdFrequencyData(
    val gameSessionCount: Int = 0,
    val lastInterstitialTime: Long = 0,
    val lastRewardedTime: Long = 0,
    val consecutiveRewardedAds: Int = 0,
    val userEngagementScore: Float = 0f
)

enum class AdTriggerAction {
    GAME_START,
    GAME_OVER,
    LEVEL_COMPLETE,
    POWER_UP_COLLECTED,
    CONTINUE_GAME
}

class AdManagerImpl(
    private val context: Context
) : AdManager {
    
    private val _adState = MutableStateFlow(AdState())
    override val adState: StateFlow<AdState> = _adState.asStateFlow()
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("ad_prefs", Context.MODE_PRIVATE)
    
    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null
    
    // Dynamic ad unit IDs based on performance data
    private val rewardedAdUnitIds = listOf(
        "ca-app-pub-3940256099942544/5224354917", // Primary
        "ca-app-pub-3940256099942544/5354046379"  // Backup
    )
    private val interstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712"
    
    // Smart frequency capping
    private val minInterstitialInterval = 180_000L // 3 minutes
    private val maxRewardedAdsPerSession = 8
    private val engagementBoostThreshold = 0.7f
    
    init {
        loadAdFrequencyData()
        // Preload ads on initialization
        loadRewardedAd()
        loadInterstitialAd()
    }
    
    override fun loadRewardedAd() {
        if (_adState.value.isLoading || _adState.value.isRewardedAdLoaded) return
        
        _adState.value = _adState.value.copy(isLoading = true)
        
        // Use A/B testing for ad unit selection
        val adUnitId = selectOptimalAdUnit()
        val adRequest = buildOptimizedAdRequest()
        
        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                    _adState.value = _adState.value.copy(
                        isRewardedAdLoaded = false,
                        isLoading = false
                    )
                    
                    // Retry with fallback strategy
                    scheduleAdRetry(AdType.REWARDED, adError.code)
                }
                
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    _adState.value = _adState.value.copy(
                        isRewardedAdLoaded = true,
                        isLoading = false
                    )
                    
                    // Track successful load for optimization
                    trackAdLoadSuccess(AdType.REWARDED)
                }
            }
        )
    }
    
    override fun loadInterstitialAd() {
        if (_adState.value.isLoading || _adState.value.isInterstitialAdLoaded) return
        
        val adRequest = buildOptimizedAdRequest()
        
        InterstitialAd.load(
            context,
            interstitialAdUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                    _adState.value = _adState.value.copy(isInterstitialAdLoaded = false)
                    scheduleAdRetry(AdType.INTERSTITIAL, adError.code)
                }
                
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    _adState.value = _adState.value.copy(isInterstitialAdLoaded = true)
                }
            }
        )
    }
    
    override fun showRewardedAd(onRewarded: (Int) -> Unit, onDismissed: () -> Unit) {
        val currentAd = rewardedAd
        
        if (currentAd == null) {
            onDismissed()
            loadRewardedAd() // Attempt to reload
            return
        }
        
        val frequencyData = _adState.value.adFrequencyData
        
        // Check frequency capping
        if (frequencyData.consecutiveRewardedAds >= maxRewardedAdsPerSession) {
            onDismissed()
            return
        }
        
        _adState.value = _adState.value.copy(isAdShowing = true)
        
        currentAd.show(context as android.app.Activity) { rewardItem ->
            // Calculate dynamic reward based on user engagement
            val baseReward = rewardItem.amount
            val engagementMultiplier = calculateEngagementMultiplier()
            val finalReward = (baseReward * engagementMultiplier).toInt()
            
            onRewarded(finalReward)
            
            // Update frequency data
            updateAdFrequencyData(AdType.REWARDED)
            
            // Reset state
            _adState.value = _adState.value.copy(
                isRewardedAdLoaded = false,
                isAdShowing = false
            )
            rewardedAd = null
            
            // Preload next ad with smart delay
            coroutineScope.launch {
                kotlinx.coroutines.delay(2000) // 2 second delay
                loadRewardedAd()
            }
        }
    }
    
    override fun showInterstitialAd(onDismissed: () -> Unit) {
        val currentAd = interstitialAd
        
        if (currentAd == null || !shouldShowInterstitialAd()) {
            onDismissed()
            return
        }
        
        _adState.value = _adState.value.copy(isAdShowing = true)
        
        currentAd.show(context as android.app.Activity)
        
        currentAd.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onDismissed()
                
                // Update frequency data
                updateAdFrequencyData(AdType.INTERSTITIAL)
                
                // Reset state
                _adState.value = _adState.value.copy(
                    isInterstitialAdLoaded = false,
                    isAdShowing = false
                )
                interstitialAd = null
                
                // Preload next ad
                loadInterstitialAd()
            }
        }
    }
    
    override fun shouldShowInterstitialAd(): Boolean {
        val frequencyData = _adState.value.adFrequencyData
        val currentTime = System.currentTimeMillis()
        
        return currentTime - frequencyData.lastInterstitialTime >= minInterstitialInterval &&
                frequencyData.gameSessionCount >= 2 && // Show after 2+ game sessions
                frequencyData.userEngagementScore > 0.3f // Only show to engaged users
    }
    
    override fun onGameAction(action: AdTriggerAction) {
        when (action) {
            AdTriggerAction.GAME_START -> {
                updateEngagementScore(0.1f)
                incrementSessionCount()
            }
            AdTriggerAction.GAME_OVER -> {
                updateEngagementScore(0.05f)
                // Consider showing interstitial after game over
            }
            AdTriggerAction.LEVEL_COMPLETE -> {
                updateEngagementScore(0.2f)
                // Higher engagement boost for level completion
            }
            AdTriggerAction.POWER_UP_COLLECTED -> {
                updateEngagementScore(0.05f)
            }
            AdTriggerAction.CONTINUE_GAME -> {
                updateEngagementScore(0.15f)
                // User choosing to continue shows high engagement
            }
        }
    }
    
    private fun selectOptimalAdUnit(): String {
        // Simple A/B testing logic - use performance data to select best ad unit
        val adPerformance = sharedPrefs.getFloat("rewarded_ad_performance_0", 0.5f)
        return if (adPerformance > 0.6f) rewardedAdUnitIds[0] else rewardedAdUnitIds.getOrElse(1) { rewardedAdUnitIds[0] }
    }
    
    private fun buildOptimizedAdRequest(): AdRequest {
        return AdRequest.Builder()
            .addKeyword("games")
            .addKeyword("arcade")
            .addKeyword("casual")
            .build()
    }
    
    private fun calculateEngagementMultiplier(): Float {
        val engagementScore = _adState.value.adFrequencyData.userEngagementScore
        return when {
            engagementScore > engagementBoostThreshold -> 1.5f
            engagementScore > 0.5f -> 1.2f
            else -> 1.0f
        }
    }
    
    private fun updateAdFrequencyData(adType: AdType) {
        val currentData = _adState.value.adFrequencyData
        val currentTime = System.currentTimeMillis()
        
        val updatedData = when (adType) {
            AdType.REWARDED -> currentData.copy(
                lastRewardedTime = currentTime,
                consecutiveRewardedAds = currentData.consecutiveRewardedAds + 1
            )
            AdType.INTERSTITIAL -> currentData.copy(
                lastInterstitialTime = currentTime
            )
        }
        
        _adState.value = _adState.value.copy(adFrequencyData = updatedData)
        saveAdFrequencyData()
    }
    
    private fun updateEngagementScore(increment: Float) {
        val currentData = _adState.value.adFrequencyData
        val newScore = min(1.0f, currentData.userEngagementScore + increment)
        
        _adState.value = _adState.value.copy(
            adFrequencyData = currentData.copy(userEngagementScore = newScore)
        )
        saveAdFrequencyData()
    }
    
    private fun incrementSessionCount() {
        val currentData = _adState.value.adFrequencyData
        _adState.value = _adState.value.copy(
            adFrequencyData = currentData.copy(
                gameSessionCount = currentData.gameSessionCount + 1,
                consecutiveRewardedAds = 0 // Reset per session
            )
        )
        saveAdFrequencyData()
    }
    
    private fun scheduleAdRetry(adType: AdType, errorCode: Int) {
        val retryDelay = when (errorCode) {
            3 -> 30_000L // Network error - retry in 30s
            2 -> 60_000L // Ad not available - retry in 1 min
            else -> 15_000L // Other errors - retry in 15s
        }
        
        coroutineScope.launch {
            kotlinx.coroutines.delay(retryDelay)
            when (adType) {
                AdType.REWARDED -> loadRewardedAd()
                AdType.INTERSTITIAL -> loadInterstitialAd()
            }
        }
    }
    
    private fun trackAdLoadSuccess(adType: AdType) {
        // Track performance metrics for optimization
        val key = when (adType) {
            AdType.REWARDED -> "rewarded_ad_performance_0"
            AdType.INTERSTITIAL -> "interstitial_ad_performance"
        }
        
        val currentPerformance = sharedPrefs.getFloat(key, 0.5f)
        val newPerformance = min(1.0f, currentPerformance + 0.05f)
        
        sharedPrefs.edit().putFloat(key, newPerformance).apply()
    }
    
    private fun loadAdFrequencyData() {
        val frequencyData = AdFrequencyData(
            gameSessionCount = sharedPrefs.getInt("game_session_count", 0),
            lastInterstitialTime = sharedPrefs.getLong("last_interstitial_time", 0),
            lastRewardedTime = sharedPrefs.getLong("last_rewarded_time", 0),
            consecutiveRewardedAds = sharedPrefs.getInt("consecutive_rewarded_ads", 0),
            userEngagementScore = sharedPrefs.getFloat("user_engagement_score", 0f)
        )
        
        _adState.value = _adState.value.copy(adFrequencyData = frequencyData)
    }
    
    private fun saveAdFrequencyData() {
        val data = _adState.value.adFrequencyData
        sharedPrefs.edit()
            .putInt("game_session_count", data.gameSessionCount)
            .putLong("last_interstitial_time", data.lastInterstitialTime)
            .putLong("last_rewarded_time", data.lastRewardedTime)
            .putInt("consecutive_rewarded_ads", data.consecutiveRewardedAds)
            .putFloat("user_engagement_score", data.userEngagementScore)
            .apply()
    }
    
    private enum class AdType {
        REWARDED,
        INTERSTITIAL
    }
}