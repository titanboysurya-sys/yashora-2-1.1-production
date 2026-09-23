package com.ritvyom.yashoraReelgenerator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.ritvyom.yashoraReelgenerator.R
import com.ritvyom.yashoraReelgenerator.data.remote.GeminiService

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Yashora Reel Generator", appName)
  }

  @Test
  fun testApiSearches() {
    val service = GeminiService()
    
    val pexelsKey = BuildConfig.PEXELS_API_KEY
    val pixabayKey = BuildConfig.PIXABAY_API_KEY
    
    println("=== TESTING PEXELS VIDEO SEARCH ===")
    val pexelsVideo = service.searchPexelsApi("steam village", pexelsKey, searchVideo = true, "9:16", 0)
    println("Pexels Video Result: '$pexelsVideo'")
    
    println("=== TESTING PEXELS PHOTO SEARCH ===")
    val pexelsPhoto = service.searchPexelsApi("steam village", pexelsKey, searchVideo = false, "9:16", 0)
    println("Pexels Photo Result: '$pexelsPhoto'")
    
    println("=== TESTING PIXABAY VIDEO SEARCH ===")
    val pixabayVideo = service.searchPixabayVideos("village", pixabayKey, "9:16", 0)
    println("Pixabay Video Result: '$pixabayVideo'")
    
    println("=== TESTING PIXABAY PHOTO SEARCH ===")
    val pixabayPhoto = service.searchPixabayApi("village", pixabayKey, "9:16", 0)
    println("Pixabay Photo Result: '$pixabayPhoto'")

    println("=== TESTING UNSPLASH PHOTO SEARCH ===")
    val unsplashPhoto = service.getBestMatchingImage("village", "realistic", 0, "village", "9:16", "Unsplash")
    println("Unsplash Photo Result: '$unsplashPhoto'")

    println("=== TESTING GEMINI AI SCRIPT ANALYSIS ===")
    // Use User's dynamic key from BuildConfig configuration
    val userGeminiKey = BuildConfig.GEMINI_API_KEY
    val script = "Create a 3-second scene of a peaceful village in the morning."
    try {
        kotlinx.coroutines.runBlocking {
            val responseList = service.analyzeScript(script, "Cinematic", "English")
            println("Gemini Parse Success! Number of Scenes: ${responseList.size}")
            if (responseList.isNotEmpty()) {
                println("Scene 1 narration: '${responseList[0].narrationText}'")
            }
        }
    } catch (e: Exception) {
        println("Gemini exception caught: ${e.message}")
    }
  }
}
