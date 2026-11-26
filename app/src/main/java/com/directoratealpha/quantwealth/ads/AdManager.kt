package com.directoratealpha.quantwealth.ads

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.directoratealpha.quantwealth.BuildConfig
import com.directoratealpha.quantwealth.ui.theme.AppColors
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdIds {
    private const val PROD_BANNER = "ca-app-pub-6157793125292238/4677634578"
    private const val PROD_INTERSTITIAL = "ca-app-pub-6157793125292238/7532570616"
    private const val PROD_REWARDED = "ca-app-pub-6157793125292238/3457594232"

    private const val TEST_BANNER = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"

    val bannerId: String get() = if (BuildConfig.DEBUG) TEST_BANNER else PROD_BANNER
    val interstitialId: String get() = if (BuildConfig.DEBUG) TEST_INTERSTITIAL else PROD_INTERSTITIAL
    val rewardedId: String get() = if (BuildConfig.DEBUG) TEST_REWARDED else PROD_REWARDED
}

class QuantAdManager(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var simRunsCounter: Int = 0

    fun initialize() {
        MobileAds.initialize(context) {
            preloadInterstitial()
            preloadRewarded()
        }
    }

    fun preloadInterstitial() {
        val req = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AdIds.interstitialId,
            req,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                }
            }
        )
    }

    fun preloadRewarded() {
        val req = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            AdIds.rewardedId,
            req,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    fun maybeShowSimulationInterstitial(activity: Activity, isPro: Boolean, onDone: () -> Unit) {
        if (isPro) {
            onDone()
            return
        }
        simRunsCounter++
        // Show discreetly on every 2nd simulation run
        if (simRunsCounter % 2 != 0) {
            onDone()
            return
        }
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    preloadInterstitial()
                    onDone()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    preloadInterstitial()
                    onDone()
                }
            }
            ad.show(activity)
        } else {
            preloadInterstitial()
            onDone()
        }
    }

    fun showRewardedAd(
        activity: Activity,
        isPro: Boolean,
        onRewardEarned: () -> Unit,
        onNotReady: () -> Unit = {}
    ) {
        if (isPro) {
            onRewardEarned()
            return
        }
        val ad = rewardedAd
        if (ad != null) {
            var rewarded = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    preloadRewarded()
                    if (rewarded) onRewardEarned()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    rewardedAd = null
                    preloadRewarded()
                    Toast.makeText(activity, "Ad unavailable right now. Please try again.", Toast.LENGTH_SHORT).show()
                    onNotReady()
                }
            }
            ad.show(activity) {
                rewarded = true
            }
        } else {
            Toast.makeText(activity, "Loading video ad... tap again in 2 seconds.", Toast.LENGTH_SHORT).show()
            preloadRewarded()
            onNotReady()
        }
    }
}

@Composable
fun BannerAdBar(isPro: Boolean) {
    if (isPro) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(AppColors.BgDeep),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = AdIds.bannerId
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }
}
