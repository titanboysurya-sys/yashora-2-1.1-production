package com.ritvyom.yashoraReelgenerator

import android.app.Application
import com.ritvyom.yashoraReelgenerator.data.local.AppDatabase
import com.ritvyom.yashoraReelgenerator.data.local.PreferencesManager
import com.ritvyom.yashoraReelgenerator.data.remote.GeminiService
import com.ritvyom.yashoraReelgenerator.data.repository.ProjectRepository
import com.ritvyom.yashoraReelgenerator.data.repository.ScriptRepository
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

class YashoraApplication : Application() {

    lateinit var repository: ProjectRepository
        private set

    lateinit var scriptRepository: ScriptRepository
        private set

    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
            Firebase.initialize(context = this)
            Firebase.appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance(),
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Proactively pre-create WebView code cache directories to suppress internal chromium opendir warnings/errors
        try {
            val cacheJs = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            val cacheWasm = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!cacheJs.exists()) cacheJs.mkdirs()
            if (!cacheWasm.exists()) cacheWasm.mkdirs()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        
        // Initialize MobileAds SDK
        try {
            MobileAds.initialize(this) { initializationStatus ->
                // Check and log the initialization status of mediation adapters
                val statusMap = initializationStatus.adapterStatusMap
                android.util.Log.d("AdMediation", "=== AdMob Mediation Initialization Map ===")
                statusMap.forEach { (adapterClass, status) ->
                    android.util.Log.d("AdMediation", "Adapter: $adapterClass | State: ${status.initializationState} | Description: ${status.description} | Latency: ${status.latency}ms")
                }
                
                // Specific verification for requested networks
                val appLovinInitialized = statusMap.keys.any { it.contains("applovin", ignoreCase = true) }
                val unityInitialized = statusMap.keys.any { it.contains("unity", ignoreCase = true) }
                val metaInitialized = statusMap.keys.any { it.contains("facebook", ignoreCase = true) || it.contains("audience", ignoreCase = true) }
                
                android.util.Log.i("AdMediation", "AppLovin Adapter Initialized: $appLovinInitialized")
                android.util.Log.i("AdMediation", "Unity Ads Adapter Initialized: $unityInitialized")
                android.util.Log.i("AdMediation", "Meta Audience Network Fallback Initialized: $metaInitialized")
                android.util.Log.d("AdMediation", "==========================================")

                com.ritvyom.yashoraReelgenerator.presentation.components.AdManager.loadAd(this)
                com.ritvyom.yashoraReelgenerator.presentation.components.RewardedAdManager.loadAd(this)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }

        // Initialize local databases and network services
        val database = AppDatabase.getDatabase(this)
        val preferencesManager = PreferencesManager(this)
        val geminiService = GeminiService()

        repository = ProjectRepository(
            context = this,
            projectDao = database.projectDao(),
            exportHistoryDao = database.exportHistoryDao(),
            cachedImageDao = database.cachedImageDao(),
            preferencesManager = preferencesManager,
            geminiService = geminiService
        )

        scriptRepository = ScriptRepository(
            context = this,
            scriptDao = database.scriptDao(),
            preferencesManager = preferencesManager,
            geminiService = geminiService
        )
    }
}
