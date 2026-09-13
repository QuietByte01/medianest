package com.medianest.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Helper class to pre-load and display Interstitial (Full Screen) ads on EXIT ONLY.
 * Enforces zero playback interruption and frequency capping.
 */
object InterstitialAdHelper {
    private const val TAG = "InterstitialAdHelper"
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var lastShownTimestampMs: Long = 0L

    // Minimum interval between full-screen exit ads (3 minutes)
    private const val MIN_AD_INTERVAL_MS = 3 * 60 * 1000L

    /**
     * Pre-loads an interstitial ad in the background.
     */
    fun preloadAd(context: Context, adUnitId: String = AdManager.TEST_INTERSTITIAL_AD_UNIT_ID) {
        if (interstitialAd != null || isLoading) return
        isLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context.applicationContext,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                    Log.d(TAG, "Interstitial Ad loaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                    Log.w(TAG, "Failed to load Interstitial Ad: ${error.message}")
                }
            }
        )
    }

    /**
     * Shows full-screen ad ONLY upon player/screen exit.
     * Guaranteed never to interrupt active media playback.
     */
    fun showAdOnExit(
        activity: Activity,
        isPlaybackActive: () -> Boolean = { false },
        onComplete: () -> Unit
    ) {
        // STRICT RULE 1: Never interrupt active playback
        if (isPlaybackActive()) {
            Log.d(TAG, "Skipping exit ad: playback is currently active")
            onComplete()
            return
        }

        // STRICT RULE 2: Frequency capping check
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastShownTimestampMs < MIN_AD_INTERVAL_MS) {
            Log.d(TAG, "Skipping exit ad: frequency cap reached")
            onComplete()
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            Log.d(TAG, "Exit ad not ready, proceeding with exit")
            preloadAd(activity)
            onComplete()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Exit ad dismissed")
                interstitialAd = null
                lastShownTimestampMs = System.currentTimeMillis()
                preloadAd(activity)
                onComplete()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "Failed to show exit ad: ${adError.message}")
                interstitialAd = null
                preloadAd(activity)
                onComplete()
            }
        }

        ad.show(activity)
    }
}
