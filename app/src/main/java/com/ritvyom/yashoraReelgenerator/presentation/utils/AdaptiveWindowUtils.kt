package com.ritvyom.yashoraReelgenerator.presentation.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Adaptive Window Size Classification and Layout Profile
 * Enables Yashora to dynamically optimize UI layout, padding, touch targets,
 * preview dimensions, and toolbars across small phones (<=320dp), normal phones,
 * and large displays (>=600dp) as well as varying system font scales (1.0x to 2.0x).
 */
enum class WindowWidthClass {
    COMPACT_SMALL, // <= 340dp (e.g. 320dp small devices)
    COMPACT_NORMAL, // 341dp .. 599dp (standard mobile devices)
    MEDIUM_EXPANDED // >= 600dp (tablets, foldables, landscape)
}

enum class WindowHeightClass {
    COMPACT, // < 600dp (small height phones, split-screen, or landscape)
    MEDIUM,  // 600dp .. 839dp (typical modern phones)
    EXPANDED // >= 840dp (tall modern phones & tablets)
}

@Immutable
data class AdaptiveLayoutProfile(
    val screenWidthDp: Dp,
    val screenHeightDp: Dp,
    val widthClass: WindowWidthClass,
    val heightClass: WindowHeightClass,
    val fontScale: Float,
    val isVerySmallWidth: Boolean,
    val isCompactHeight: Boolean,
    val isWideScreen: Boolean,
    val isLargeFont: Boolean,
    val recommendedTimelineHeight: Dp,
    val recommendedToolbarHeight: Dp,
    val maxSheetHeightPercent: Float
) {
    /**
     * Calculates the optimal preview size based on the chosen aspect ratio and available container dimensions.
     * Prevents the video preview from dominating small screens and pushing controls off-screen.
     */
    fun calculateAdaptivePreviewHeight(aspectRatio: String, maxContainerHeight: Dp): Dp {
        val baseMax = if (isCompactHeight) {
            (screenHeightDp * 0.28f).coerceIn(110.dp, 160.dp)
        } else if (isVerySmallWidth) {
            (screenHeightDp * 0.32f).coerceIn(130.dp, 190.dp)
        } else {
            (screenHeightDp * 0.35f).coerceIn(150.dp, 240.dp)
        }

        val constrainedMax = min(baseMax.value, maxContainerHeight.value).dp

        return when (aspectRatio) {
            "16:9" -> (constrainedMax * 0.75f).coerceAtLeast(100.dp)
            "21:9" -> (constrainedMax * 0.58f).coerceAtLeast(85.dp)
            "1:1" -> (constrainedMax * 0.88f).coerceAtLeast(120.dp)
            "4:5" -> (constrainedMax * 0.92f).coerceAtLeast(130.dp)
            "3:4" -> (constrainedMax * 0.94f).coerceAtLeast(135.dp)
            else -> constrainedMax // 9:16 portrait
        }
    }
}

object AdaptiveWindowUtils {
    fun calculateProfile(
        screenWidth: Dp,
        screenHeight: Dp,
        fontScale: Float = 1.0f
    ): AdaptiveLayoutProfile {
        val widthClass = when {
            screenWidth <= 340.dp -> WindowWidthClass.COMPACT_SMALL
            screenWidth < 600.dp -> WindowWidthClass.COMPACT_NORMAL
            else -> WindowWidthClass.MEDIUM_EXPANDED
        }

        val heightClass = when {
            screenHeight < 600.dp -> WindowHeightClass.COMPACT
            screenHeight < 840.dp -> WindowHeightClass.MEDIUM
            else -> WindowHeightClass.EXPANDED
        }

        val isVerySmallWidth = widthClass == WindowWidthClass.COMPACT_SMALL
        val isCompactHeight = heightClass == WindowHeightClass.COMPACT
        val isWideScreen = widthClass == WindowWidthClass.MEDIUM_EXPANDED
        val isLargeFont = fontScale >= 1.25f

        val timelineHeight = when {
            isCompactHeight -> 72.dp
            isVerySmallWidth -> 76.dp
            isWideScreen -> 96.dp
            else -> 84.dp
        }

        val toolbarHeight = when {
            isCompactHeight -> 52.dp
            isLargeFont -> 64.dp
            else -> 58.dp
        }

        val maxSheetHeight = when {
            isCompactHeight -> 0.88f
            else -> 0.82f
        }

        return AdaptiveLayoutProfile(
            screenWidthDp = screenWidth,
            screenHeightDp = screenHeight,
            widthClass = widthClass,
            heightClass = heightClass,
            fontScale = fontScale,
            isVerySmallWidth = isVerySmallWidth,
            isCompactHeight = isCompactHeight,
            isWideScreen = isWideScreen,
            isLargeFont = isLargeFont,
            recommendedTimelineHeight = timelineHeight,
            recommendedToolbarHeight = toolbarHeight,
            maxSheetHeightPercent = maxSheetHeight
        )
    }
}

@Composable
fun rememberAdaptiveLayoutProfile(): AdaptiveLayoutProfile {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val fontScale = density.fontScale

    return remember(screenWidth, screenHeight, fontScale) {
        AdaptiveWindowUtils.calculateProfile(screenWidth, screenHeight, fontScale)
    }
}
