package com.boltgame.brickbreakerroguelite.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.boltgame.brickbreakerroguelite.R
import com.boltgame.brickbreakerroguelite.databinding.ActivityMainBinding
import com.boltgame.brickbreakerroguelite.monetization.ads.AdManager
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    
    private val adManager: AdManager by inject()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up Navigation
        val navHostFragment = supportFragmentManager.findFragmentById(
            R.id.nav_host_fragment
        ) as NavHostFragment
        navController = navHostFragment.navController
        
        // Set up bottom navigation
        binding.bottomNavigation.setupWithNavController(navController)
        
        // Hide bottom navigation on game screen
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.gameFragment -> binding.bottomNavigation.visibility = android.view.View.GONE
                else -> binding.bottomNavigation.visibility = android.view.View.VISIBLE
            }
        }
        
        // Preload a rewarded ad
        adManager.loadRewardedAd()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}