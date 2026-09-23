package com.ritvyom.yashoraReelgenerator

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import com.ritvyom.yashoraReelgenerator.data.local.AppDatabase
import com.ritvyom.yashoraReelgenerator.data.local.PreferencesManager
import com.ritvyom.yashoraReelgenerator.data.remote.GeminiService
import com.ritvyom.yashoraReelgenerator.data.repository.ProjectRepository
import com.ritvyom.yashoraReelgenerator.data.repository.ScriptRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import java.util.concurrent.Executors

class YashoraApplication : Application(), ImageLoaderFactory {

    companion object {
        private var instance: YashoraApplication? = null
        fun getInstance(): YashoraApplication? = instance
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val original = chain.request()
                val urlStr = original.url.toString()
                val ua = when {
                    urlStr.contains("nekos.best", ignoreCase = true) ->
                        "YashoraReelGenerator (Ritvyom@gmail.com)"
                    urlStr.contains("wikimedia.org", ignoreCase = true) || urlStr.contains("wikipedia.org", ignoreCase = true) ->
                        "YashoraReelGenerator/2.0 (https://ai.studio; yashoratechnologies@gmail.com) Android/14 OkHttp/4.12"
                    else ->
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                }
                val request = original.newBuilder()
                    .header("User-Agent", ua)
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }

    // Lazy initialization for AppDatabase and PreferencesManager to reduce memory footprint on startup
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(this)
    }

    val geminiService: GeminiService by lazy {
        GeminiService()
    }

    val secureAiCredentialStore: com.ritvyom.yashoraReelgenerator.data.ai.SecureAiCredentialStore by lazy {
        com.ritvyom.yashoraReelgenerator.data.ai.SecureAiCredentialStore(this)
    }

    val unifiedAiRouter: com.ritvyom.yashoraReelgenerator.data.ai.UnifiedAiRouter by lazy {
        com.ritvyom.yashoraReelgenerator.data.ai.UnifiedAiRouter(
            context = this,
            credentialStore = secureAiCredentialStore,
            preferencesManager = preferencesManager
        )
    }

    // Lazy initialization for Repositories - only initialized when first accessed
    val repository: ProjectRepository by lazy {
        ProjectRepository(
            context = this,
            projectDao = database.projectDao(),
            exportHistoryDao = database.exportHistoryDao(),
            cachedImageDao = database.cachedImageDao(),
            communityVideoCacheDao = database.communityVideoCacheDao(),
            preferencesManager = preferencesManager,
            geminiService = geminiService
        )
    }

    val scriptRepository: ScriptRepository by lazy {
        ScriptRepository(
            context = this,
            scriptDao = database.scriptDao(),
            preferencesManager = preferencesManager,
            geminiService = geminiService
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Defer Firebase and cache setup asynchronously to background executor to keep main thread completely unblocked
        Executors.newSingleThreadExecutor().execute {
            try {
                if (FirebaseApp.getApps(this).isEmpty()) {
                    FirebaseApp.initializeApp(this)
                }
                Firebase.initialize(context = this)
                try {
                    if (!BuildConfig.DEBUG) {
                        Firebase.appCheck.installAppCheckProviderFactory(
                            PlayIntegrityAppCheckProviderFactory.getInstance()
                        )
                    }
                } catch (appCheckEx: Exception) {
                    Log.w("YashoraApplication", "Firebase App Check initialization skipped: ${appCheckEx.message}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Pre-create WebView code cache directories to suppress internal chromium opendir warnings
            try {
                val cacheJs = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
                val cacheWasm = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
                if (!cacheJs.exists()) cacheJs.mkdirs()
                if (!cacheWasm.exists()) cacheWasm.mkdirs()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Trim memory levels for background and UI hidden scenarios
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || level >= 40) {
            com.ritvyom.yashoraReelgenerator.data.remote.MediaSelectionService.clearCache()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        com.ritvyom.yashoraReelgenerator.data.remote.MediaSelectionService.clearCache()
    }
}
