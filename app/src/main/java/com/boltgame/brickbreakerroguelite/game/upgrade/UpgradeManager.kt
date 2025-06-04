package com.boltgame.brickbreakerroguelite.game.upgrade

import com.boltgame.brickbreakerroguelite.data.model.PermanentUpgrade
import com.boltgame.brickbreakerroguelite.data.repository.GameProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UpgradeManager {
    fun getAvailableUpgrades(): Flow<List<UpgradeInfo>>
    fun getUpgradeInfo(upgradeId: String): Flow<UpgradeInfo?>
    fun getUpgradeEffect(upgradeId: String, level: Int): UpgradeEffect
    suspend fun purchaseUpgrade(upgradeId: String): Boolean
}

data class UpgradeInfo(
    val id: String,
    val name: String,
    val description: String,
    val currentLevel: Int,
    val maxLevel: Int,
    val cost: Int,
    val effect: UpgradeEffect
)

data class UpgradeEffect(
    val paddleSizeMultiplier: Float = 1f,
    val ballDamageMultiplier: Float = 1f,
    val startingLives: Int = 3,
    val powerUpDurationMultiplier: Float = 1f,
    val scoreMultiplier: Float = 1f
)

class UpgradeManagerImpl(
    private val gameProgressRepository: GameProgressRepository
) : UpgradeManager {
    
    private val upgradeDefinitions = mapOf(
        "paddle_size" to UpgradeDefinition(
            name = "Bigger Paddle",
            description = "Increases paddle size",
            baseCost = 100,
            costMultiplier = 1.5f,
            effectMultipliers = listOf(1.0f, 1.1f, 1.2f, 1.3f, 1.5f, 1.75f)
        ),
        "ball_damage" to UpgradeDefinition(
            name = "Stronger Ball",
            description = "Increases ball damage",
            baseCost = 150,
            costMultiplier = 1.6f,
            effectMultipliers = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f)
        ),
        "extra_life" to UpgradeDefinition(
            name = "Extra Life",
            description = "Start with more lives",
            baseCost = 200,
            costMultiplier = 2.0f,
            effectValues = listOf(3, 4, 5, 6, 7, 8)
        ),
        "power_duration" to UpgradeDefinition(
            name = "Power Endurance",
            description = "Power-ups last longer",
            baseCost = 125,
            costMultiplier = 1.4f,
            effectMultipliers = listOf(1.0f, 1.15f, 1.3f, 1.45f, 1.6f, 1.8f)
        ),
        "score_bonus" to UpgradeDefinition(
            name = "Score Bonus",
            description = "Increases points earned",
            baseCost = 175,
            costMultiplier = 1.7f,
            effectMultipliers = listOf(1.0f, 1.1f, 1.2f, 1.35f, 1.5f, 1.75f)
        )
    )
    
    override fun getAvailableUpgrades(): Flow<List<UpgradeInfo>> {
        return gameProgressRepository.getGameProgress().map { progress ->
            upgradeDefinitions.map { (id, definition) ->
                val existing = progress.permanentUpgrades.find { it.id == id }
                val currentLevel = existing?.level ?: 0
                val maxLevel = existing?.maxLevel ?: 5
                
                UpgradeInfo(
                    id = id,
                    name = definition.name,
                    description = definition.description,
                    currentLevel = currentLevel,
                    maxLevel = maxLevel,
                    cost = calculateCost(definition, currentLevel),
                    effect = getUpgradeEffect(id, currentLevel)
                )
            }
        }
    }
    
    override fun getUpgradeInfo(upgradeId: String): Flow<UpgradeInfo?> {
        return gameProgressRepository.getGameProgress().map { progress ->
            val definition = upgradeDefinitions[upgradeId] ?: return@map null
            val existing = progress.permanentUpgrades.find { it.id == upgradeId }
            val currentLevel = existing?.level ?: 0
            val maxLevel = existing?.maxLevel ?: 5
            
            UpgradeInfo(
                id = upgradeId,
                name = definition.name,
                description = definition.description,
                currentLevel = currentLevel,
                maxLevel = maxLevel,
                cost = calculateCost(definition, currentLevel),
                effect = getUpgradeEffect(upgradeId, currentLevel)
            )
        }
    }
    
    override fun getUpgradeEffect(upgradeId: String, level: Int): UpgradeEffect {
        val definition = upgradeDefinitions[upgradeId] ?: return UpgradeEffect()
        val effectIndex = level.coerceAtMost(definition.effectMultipliers.size - 1)
        
        return when (upgradeId) {
            "paddle_size" -> UpgradeEffect(
                paddleSizeMultiplier = definition.effectMultipliers.getOrElse(effectIndex) { 1f }
            )
            "ball_damage" -> UpgradeEffect(
                ballDamageMultiplier = definition.effectMultipliers.getOrElse(effectIndex) { 1f }
            )
            "extra_life" -> UpgradeEffect(
                startingLives = definition.effectValues.getOrElse(effectIndex) { 3 }
            )
            "power_duration" -> UpgradeEffect(
                powerUpDurationMultiplier = definition.effectMultipliers.getOrElse(effectIndex) { 1f }
            )
            "score_bonus" -> UpgradeEffect(
                scoreMultiplier = definition.effectMultipliers.getOrElse(effectIndex) { 1f }
            )
            else -> UpgradeEffect()
        }
    }
    
    override suspend fun purchaseUpgrade(upgradeId: String): Boolean {
        val progress = gameProgressRepository.getGameProgress().value
        val existing = progress.permanentUpgrades.find { it.id == upgradeId }
        val currentLevel = existing?.level ?: 0
        
        if (currentLevel >= 5) {
            return false  // Already maxed out
        }
        
        val definition = upgradeDefinitions[upgradeId] ?: return false
        val cost = calculateCost(definition, currentLevel)
        
        // Try to spend currency
        if (!gameProgressRepository.spendSoftCurrency(cost)) {
            return false  // Not enough currency
        }
        
        // Purchase successful, upgrade
        return gameProgressRepository.upgradePermanent(upgradeId)
    }
    
    private fun calculateCost(definition: UpgradeDefinition, currentLevel: Int): Int {
        val nextLevel = currentLevel + 1
        if (nextLevel > 5) return Int.MAX_VALUE  // Max level reached
        
        return (definition.baseCost * Math.pow(definition.costMultiplier.toDouble(), currentLevel.toDouble())).toInt()
    }
    
    private data class UpgradeDefinition(
        val name: String,
        val description: String,
        val baseCost: Int,
        val costMultiplier: Float,
        val effectMultipliers: List<Float> = emptyList(),
        val effectValues: List<Int> = emptyList()
    )
}