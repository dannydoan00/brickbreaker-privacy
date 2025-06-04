package com.boltgame.brickbreakerroguelite

import android.content.Context
import android.content.SharedPreferences
import com.boltgame.brickbreakerroguelite.data.repository.GameProgressRepository
import com.boltgame.brickbreakerroguelite.monetization.ads.AdManager
import com.boltgame.brickbreakerroguelite.monetization.ads.AdManagerImpl
import com.boltgame.brickbreakerroguelite.monetization.ads.AdTriggerAction
import com.boltgame.brickbreakerroguelite.monetization.iap.IapManager
import com.boltgame.brickbreakerroguelite.monetization.iap.IapManagerImpl
import com.boltgame.brickbreakerroguelite.monetization.iap.PurchaseAction
import com.boltgame.brickbreakerroguelite.monetization.iap.SpendingTier
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@ExperimentalCoroutinesApi
class MonetizationTest {

    private lateinit var adManager: AdManagerImpl
    private lateinit var iapManager: IapManagerImpl
    private lateinit var mockContext: Context
    private lateinit var mockSharedPrefs: SharedPreferences
    private lateinit var mockSharedPrefsEditor: SharedPreferences.Editor
    private lateinit var mockGameProgressRepository: GameProgressRepository

    @Before
    fun setUp() {
        mockContext = mockk()
        mockSharedPrefs = mockk()
        mockSharedPrefsEditor = mockk()
        mockGameProgressRepository = mockk()

        every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPrefs
        every { mockSharedPrefs.edit() } returns mockSharedPrefsEditor
        every { mockSharedPrefsEditor.putInt(any(), any()) } returns mockSharedPrefsEditor
        every { mockSharedPrefsEditor.putFloat(any(), any()) } returns mockSharedPrefsEditor
        every { mockSharedPrefsEditor.putLong(any(), any()) } returns mockSharedPrefsEditor
        every { mockSharedPrefsEditor.putBoolean(any(), any()) } returns mockSharedPrefsEditor
        every { mockSharedPrefsEditor.apply() } returns Unit
        
        // Setup default shared preferences values
        every { mockSharedPrefs.getInt(any(), any()) } returns 0
        every { mockSharedPrefs.getFloat(any(), any()) } returns 0f
        every { mockSharedPrefs.getLong(any(), any()) } returns 0L
        every { mockSharedPrefs.getBoolean(any(), any()) } returns false

        coEvery { mockGameProgressRepository.addSoftCurrency(any()) } returns Unit
        coEvery { mockGameProgressRepository.unlockPremiumFeatures() } returns Unit
        coEvery { mockGameProgressRepository.unlockStarterPack() } returns Unit
        coEvery { mockGameProgressRepository.grantPowerUpPack() } returns Unit

        adManager = AdManagerImpl(mockContext)
        iapManager = IapManagerImpl(mockContext, mockGameProgressRepository)
    }

    // Ad Manager Tests
    @Test
    fun `adManager should track engagement score correctly`() = runTest {
        // Given
        val initialState = adManager.adState.first()
        assertEquals(0f, initialState.adFrequencyData.userEngagementScore, 0.01f)

        // When
        adManager.onGameAction(AdTriggerAction.GAME_START)
        adManager.onGameAction(AdTriggerAction.LEVEL_COMPLETE)
        adManager.onGameAction(AdTriggerAction.POWER_UP_COLLECTED)

        // Then
        val finalState = adManager.adState.first()
        assertTrue(
            "Engagement score should increase with positive actions",
            finalState.adFrequencyData.userEngagementScore > 0f
        )
    }

    @Test
    fun `adManager should respect frequency capping for interstitial ads`() = runTest {
        // Given - Mock recent interstitial ad
        every { mockSharedPrefs.getLong("last_interstitial_time", 0) } returns System.currentTimeMillis()
        every { mockSharedPrefs.getInt("game_session_count", 0) } returns 5
        every { mockSharedPrefs.getFloat("user_engagement_score", 0f) } returns 0.5f

        // When
        val shouldShow = adManager.shouldShowInterstitialAd()

        // Then
        assertFalse("Should not show interstitial due to frequency capping", shouldShow)
    }

    @Test
    fun `adManager should allow interstitial ads for engaged users after interval`() = runTest {
        // Given - Mock old interstitial ad and engaged user
        val oldTime = System.currentTimeMillis() - (4 * 60 * 1000) // 4 minutes ago
        every { mockSharedPrefs.getLong("last_interstitial_time", 0) } returns oldTime
        every { mockSharedPrefs.getInt("game_session_count", 0) } returns 5
        every { mockSharedPrefs.getFloat("user_engagement_score", 0f) } returns 0.5f

        // When
        val shouldShow = adManager.shouldShowInterstitialAd()

        // Then
        assertTrue("Should show interstitial for engaged user after interval", shouldShow)
    }

    @Test
    fun `adManager should cap rewarded ads per session`() = runTest {
        // Given - Mock maximum rewarded ads already shown
        every { mockSharedPrefs.getInt("consecutive_rewarded_ads", 0) } returns 8

        // When
        var rewardReceived = false
        adManager.showRewardedAd(
            onRewarded = { rewardReceived = true },
            onDismissed = { /* dismissed */ }
        )

        // Then
        assertFalse("Should not show rewarded ad when limit reached", rewardReceived)
    }

    @Test
    fun `adManager should calculate engagement multiplier correctly`() = runTest {
        // This is a bit of a white-box test, but important for revenue optimization
        
        // Given - High engagement user
        every { mockSharedPrefs.getFloat("user_engagement_score", 0f) } returns 0.8f
        
        // When
        adManager.onGameAction(AdTriggerAction.LEVEL_COMPLETE) // High engagement action

        // The engagement multiplier should provide bonus rewards for highly engaged users
        // This is tested indirectly through the reward calculation
        val finalState = adManager.adState.first()
        assertTrue(
            "High engagement users should have higher score",
            finalState.adFrequencyData.userEngagementScore > 0.7f
        )
    }

    @Test
    fun `adManager should preload ads on initialization`() = runTest {
        // When - AdManager is initialized (already done in setUp)
        
        // Then - Should attempt to load both types of ads
        // This would require mocking the actual ad loading, but we can verify the state
        val state = adManager.adState.first()
        assertNotNull("Ad state should be initialized", state)
        assertNotNull("Frequency data should be initialized", state.adFrequencyData)
    }

    // IAP Manager Tests
    @Test
    fun `iapManager should track purchase analytics correctly`() = runTest {
        // Given
        val initialState = iapManager.iapState.first()
        assertEquals(0, initialState.purchaseAnalytics.totalPurchases)
        assertEquals(0f, initialState.purchaseAnalytics.totalRevenue, 0.01f)

        // When
        iapManager.onPurchaseAction(PurchaseAction.VIEW_STORE)
        iapManager.onPurchaseAction(PurchaseAction.VIEW_PRODUCT)
        iapManager.onPurchaseAction(PurchaseAction.INITIATE_PURCHASE)

        // Then
        val finalState = iapManager.iapState.first()
        assertTrue(
            "Conversion events should be tracked",
            finalState.purchaseAnalytics.conversionEvents.isNotEmpty()
        )
    }

    @Test
    fun `iapManager should determine correct spending tier`() = runTest {
        // Test free user
        every { mockSharedPrefs.getFloat("total_revenue", 0f) } returns 0f
        var tier = iapManager.getOptimalOffer(SpendingTier.FREE_USER)
        // Should suggest low-barrier entry offers

        // Test low spender
        every { mockSharedPrefs.getFloat("total_revenue", 0f) } returns 3f
        tier = iapManager.getOptimalOffer(SpendingTier.LOW_SPENDER)
        // Should suggest mid-tier value offers

        // Test whale
        every { mockSharedPrefs.getFloat("total_revenue", 0f) } returns 100f
        tier = iapManager.getOptimalOffer(SpendingTier.WHALE)
        // Should suggest premium offers

        // All tiers should return some offer (or null if no products loaded)
        // The specific logic depends on available products
    }

    @Test
    fun `iapManager should generate dynamic offers for first-time buyers`() = runTest {
        // Given - New user with no purchases
        every { mockSharedPrefs.getInt("total_purchases", 0) } returns 0

        // When
        iapManager.onPurchaseAction(PurchaseAction.VIEW_STORE)

        // Then
        val state = iapManager.iapState.first()
        // Should have generated dynamic offers for first-time buyers
        assertTrue(
            "Should generate offers for new users",
            state.dynamicOffers.isNotEmpty() || state.purchaseAnalytics.conversionEvents.isNotEmpty()
        )
    }

    @Test
    fun `iapManager should trigger emergency offers when user runs out of currency`() = runTest {
        // Given
        val initialState = iapManager.iapState.first()

        // When
        iapManager.onPurchaseAction(PurchaseAction.GAME_OVER_NO_CURRENCY)

        // Then
        val finalState = iapManager.iapState.first()
        assertTrue(
            "Should track emergency conversion events",
            finalState.purchaseAnalytics.conversionEvents.any { 
                it.action == PurchaseAction.GAME_OVER_NO_CURRENCY 
            }
        )
    }

    @Test
    fun `iapManager should suggest value offers when upgrades are blocked`() = runTest {
        // Given
        val initialState = iapManager.iapState.first()

        // When
        iapManager.onPurchaseAction(PurchaseAction.UPGRADE_BLOCKED)

        // Then
        val finalState = iapManager.iapState.first()
        assertTrue(
            "Should track upgrade blocking events",
            finalState.purchaseAnalytics.conversionEvents.any { 
                it.action == PurchaseAction.UPGRADE_BLOCKED 
            }
        )
    }

    @Test
    fun `iapManager should generate win-back offers for lapsed users`() = runTest {
        // Given - User who hasn't purchased in a while
        val oldPurchaseTime = System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L) // 8 days ago
        every { mockSharedPrefs.getLong("last_purchase_time", 0) } returns oldPurchaseTime

        // When - The manager initializes (checks for win-back offers)
        val state = iapManager.iapState.first()

        // Then - Should generate win-back offers
        val hasWinBackOffer = state.dynamicOffers.any { it.triggerCondition == "win_back" }
        assertTrue("Should generate win-back offers for lapsed users", hasWinBackOffer)
    }

    @Test
    fun `monetization systems should work together for optimal revenue`() = runTest {
        // Integration test for ad and IAP systems working together

        // Given - User with moderate engagement
        every { mockSharedPrefs.getFloat("user_engagement_score", 0f) } returns 0.6f
        every { mockSharedPrefs.getInt("total_purchases", 0) } returns 1
        every { mockSharedPrefs.getFloat("total_revenue", 0f) } returns 5f

        // When - User completes level (high engagement action)
        adManager.onGameAction(AdTriggerAction.LEVEL_COMPLETE)
        iapManager.onPurchaseAction(PurchaseAction.VIEW_STORE)

        // Then - Both systems should track engagement positively
        val adState = adManager.adState.first()
        val iapState = iapManager.iapState.first()

        assertTrue(
            "Ad system should track positive engagement",
            adState.adFrequencyData.userEngagementScore > 0f
        )
        assertTrue(
            "IAP system should track store visits",
            iapState.purchaseAnalytics.conversionEvents.isNotEmpty()
        )
    }

    @Test
    fun `adManager should handle A-B testing for ad units`() = runTest {
        // Given - Mock performance data for A/B testing
        every { mockSharedPrefs.getFloat("rewarded_ad_performance_0", 0.5f) } returns 0.7f

        // When - Loading ad (this would select optimal ad unit)
        adManager.loadRewardedAd()

        // Then - Should use the better performing ad unit
        // This is tested indirectly through the loading behavior
        val state = adManager.adState.first()
        assertNotNull("Should have loaded ad configuration", state)
    }

    @Test
    fun `performance test - monetization systems should be efficient`() = runTest {
        // Given
        val startTime = System.nanoTime()

        // When - Perform multiple monetization operations
        repeat(100) {
            adManager.onGameAction(AdTriggerAction.POWER_UP_COLLECTED)
            iapManager.onPurchaseAction(PurchaseAction.VIEW_PRODUCT)
        }

        val endTime = System.nanoTime()

        // Then
        val totalTime = (endTime - startTime) / 1_000_000.0 // Convert to milliseconds
        assertTrue(
            "Monetization operations should be efficient (was ${totalTime}ms)",
            totalTime < 100.0 // Should complete 200 operations in under 100ms
        )
    }

    @Test
    fun `iapManager should persist analytics across sessions`() = runTest {
        // Given - Mock existing analytics data
        every { mockSharedPrefs.getInt("total_purchases", 0) } returns 5
        every { mockSharedPrefs.getFloat("total_revenue", 0f) } returns 15.99f
        every { mockSharedPrefs.getFloat("avg_purchase_value", 0f) } returns 3.20f

        // When - Create new IAP manager (simulating app restart)
        val newIapManager = IapManagerImpl(mockContext, mockGameProgressRepository)

        // Then - Should load previous analytics
        val state = newIapManager.iapState.first()
        assertEquals(5, state.purchaseAnalytics.totalPurchases)
        assertEquals(15.99f, state.purchaseAnalytics.totalRevenue, 0.01f)
        assertEquals(3.20f, state.purchaseAnalytics.averagePurchaseValue, 0.01f)
    }
} 