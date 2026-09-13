package com.medianest.ads

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Singleton manager responsible for initializing Google Mobile Ads SDK safely and asynchronously.
 */
object AdManager {
    private const val TAG = "AdManager"
    private val isInitialized = AtomicBoolean(false)

    // Default AdMob Test Unit IDs provided by Google for development/testing
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    fun initialize(context: Context) {
        if (isInitialized.getAndSet(true)) return

        try {
            // Configure test device request settings
            val requestConfiguration = RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(com.google.android.gms.ads.AdRequest.DEVICE_ID_EMULATOR))
                .build()
            MobileAds.setRequestConfiguration(requestConfiguration)

            // Initialize Mobile Ads SDK on background thread
            MobileAds.initialize(context.applicationContext) { status ->
                Log.d(TAG, "Google Mobile Ads SDK Initialized: ${status.adapterStatusMap}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Google Mobile Ads SDK", e)
        }
    }

    fun isSdkInitialized(): Boolean = isInitialized.get()
}
