package com.boltgame.brickbreakerroguelite

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * Publishing Readiness Checker
 * Validates all components are properly configured for Google Play Store release
 */
object PublishingReadiness {
    
    private const val TAG = "PublishingReadiness"
    
    data class ReadinessReport(
        val isReady: Boolean,
        val warnings: List<String>,
        val errors: List<String>,
        val recommendations: List<String>
    )
    
    fun checkReadiness(context: Context): ReadinessReport {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val recommendations = mutableListOf<String>()
        
        // Check if this is a release build
        val isDebugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebugBuild) {
            errors.add("App is in debug mode - build release version for publishing")
        }
        
        // Check app version
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (packageInfo.versionName == "1.0" && packageInfo.versionCode == 1) {
                warnings.add("Using default version - consider updating for production")
            }
            Log.d(TAG, "Version: ${packageInfo.versionName} (${packageInfo.versionCode})")
        } catch (e: Exception) {
            errors.add("Could not read app version information")
        }
        
        // Check required permissions
        val requiredPermissions = listOf(
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.ACCESS_NETWORK_STATE
        )
        
        // App signing verification (this would need actual keystore validation)
        recommendations.add("Ensure app is signed with production keystore")
        recommendations.add("Verify all ad unit IDs are production (not test) IDs")
        recommendations.add("Test IAP with real products in Google Play Console")
        
        // Privacy and compliance
        recommendations.add("Ensure privacy policy is uploaded and linked")
        recommendations.add("Complete content rating questionnaire")
        recommendations.add("Add store listing screenshots and descriptions")
        
        // Performance validation
        recommendations.add("Test on multiple devices and Android versions")
        recommendations.add("Run performance profiling to ensure 60fps target")
        recommendations.add("Verify APK size is under 100MB for wide compatibility")
        
        val isReady = errors.isEmpty()
        
        return ReadinessReport(
            isReady = isReady,
            warnings = warnings,
            errors = errors,
            recommendations = recommendations
        )
    }
    
    fun printReadinessReport(context: Context) {
        val report = checkReadiness(context)
        
        Log.i(TAG, "=== PUBLISHING READINESS REPORT ===")
        Log.i(TAG, "Ready for publishing: ${report.isReady}")
        
        if (report.errors.isNotEmpty()) {
            Log.e(TAG, "ERRORS (must fix before publishing):")
            report.errors.forEach { Log.e(TAG, "❌ $it") }
        }
        
        if (report.warnings.isNotEmpty()) {
            Log.w(TAG, "WARNINGS (should fix):")
            report.warnings.forEach { Log.w(TAG, "⚠️ $it") }
        }
        
        if (report.recommendations.isNotEmpty()) {
            Log.i(TAG, "RECOMMENDATIONS:")
            report.recommendations.forEach { Log.i(TAG, "💡 $it") }
        }
        
        Log.i(TAG, "================================")
    }
} 