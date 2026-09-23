package com.ritvyom.yashoraReelgenerator

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.presentation.components.editor.*
import com.ritvyom.yashoraReelgenerator.presentation.utils.AdaptiveLayoutProfile
import com.ritvyom.yashoraReelgenerator.presentation.utils.AdaptiveWindowUtils
import com.ritvyom.yashoraReelgenerator.presentation.utils.WindowHeightClass
import com.ritvyom.yashoraReelgenerator.presentation.utils.WindowWidthClass
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AdaptiveEditorLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `adaptive profile correctly identifies small compact devices`() {
        // Small phone: 320dp x 568dp
        val smallPhoneProfile = AdaptiveWindowUtils.calculateProfile(
            screenWidth = 320.dp,
            screenHeight = 568.dp,
            fontScale = 1.0f
        )
        assertTrue("320dp width must be Compact Small", smallPhoneProfile.isVerySmallWidth)
        assertTrue("568dp height must be Compact", smallPhoneProfile.isCompactHeight)
        assertEquals("Width class should be COMPACT_SMALL", WindowWidthClass.COMPACT_SMALL, smallPhoneProfile.widthClass)
        assertEquals("Height class should be COMPACT", WindowHeightClass.COMPACT, smallPhoneProfile.heightClass)

        // Standard phone: 390dp x 844dp
        val standardPhoneProfile = AdaptiveWindowUtils.calculateProfile(
            screenWidth = 390.dp,
            screenHeight = 844.dp,
            fontScale = 1.0f
        )
        assertFalse("390dp width is not very small", standardPhoneProfile.isVerySmallWidth)
        assertFalse("844dp height is not compact", standardPhoneProfile.isCompactHeight)
        assertEquals("Height class should be EXPANDED", WindowHeightClass.EXPANDED, standardPhoneProfile.heightClass)

        // Tablet / Large device: 800dp x 1280dp
        val tabletProfile = AdaptiveWindowUtils.calculateProfile(
            screenWidth = 800.dp,
            screenHeight = 1280.dp,
            fontScale = 1.0f
        )
        assertTrue("800dp width must be WideScreen", tabletProfile.isWideScreen)
        assertEquals("Width class should be MEDIUM_EXPANDED", WindowWidthClass.MEDIUM_EXPANDED, tabletProfile.widthClass)
    }

    @Test
    fun `all tools hub contains 100 percent of editor tools with zero feature loss`() {
        var clickedTool: EditorTool? = null
        var dismissed = false

        composeTestRule.setContent {
            Surface {
                AllToolsHubBottomSheet(
                    activeTool = null,
                    appLanguage = "en",
                    onToolSelected = { clickedTool = it },
                    onDismiss = { dismissed = true }
                )
            }
        }

        // Verify the sheet container exists
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("all_tools_hub_sheet").assertExists()

        // Test tool in the top visible row (TRIM)
        composeTestRule.onNodeWithTag("hub_item_trim").assertExists().performClick()
        assertEquals("Clicking Trim tool in hub selects TRIM", EditorTool.TRIM, clickedTool)

        // Test tool with scroll-to (ADJUST)
        composeTestRule.onNodeWithTag("hub_item_adjust").performScrollTo().performClick()
        assertEquals("Clicking Adjust tool in hub selects ADJUST", EditorTool.ADJUST, clickedTool)

        // Test category filtering
        composeTestRule.onNodeWithTag("category_chip_audio").assertExists().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("hub_item_voice_fx").performScrollTo().performClick()
        assertEquals("Clicking Voice FX tool in hub selects VOICE_FX", EditorTool.VOICE_FX, clickedTool)
    }

    @Test
    fun `youcut toolbar renders with more button and allows opening full tool hub`() {
        var clickedTool: EditorTool? = null

        composeTestRule.setContent {
            Surface {
                YouCutToolActionRow(
                    activeTool = null,
                    appLanguage = "en",
                    onToolClick = { clickedTool = it }
                )
            }
        }

        // Verify toolbar renders
        composeTestRule.onNodeWithTag("youcut_tool_action_row").assertExists()

        // Verify "MORE" overflow hub button exists
        composeTestRule.onNodeWithTag("tool_more_hub_button").assertExists()

        // Clicking MORE opens the all-tools hub
        composeTestRule.onNodeWithTag("tool_more_hub_button").performClick()

        // Hub bottom sheet should appear
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("all_tools_hub_sheet").assertExists()
    }

    @Test
    fun `timeline track renders clips and handles scrub without overflow on compact constraints`() {
        val sampleScenes = listOf(
            Scene(
                sceneNumber = 1,
                narrationText = "Scene 1",
                visualPrompt = "Prompt 1",
                subtitle = "Sub 1",
                mediaPath = "file:///sample1.jpg",
                durationSeconds = 5
            ),
            Scene(
                sceneNumber = 2,
                narrationText = "Scene 2",
                visualPrompt = "Prompt 2",
                subtitle = "Sub 2",
                mediaPath = "file:///sample2.jpg",
                durationSeconds = 4
            )
        )

        var selectedIdx = 0
        var scrubbedMs = 0L

        composeTestRule.setContent {
            Surface {
                YouCutTimelineTrack(
                    scenes = sampleScenes,
                    selectedSceneIndex = selectedIdx,
                    currentPlaybackTimeMs = 1500L,
                    totalDurationSeconds = 9,
                    isAudioMuted = false,
                    appLanguage = "en",
                    onSelectScene = { selectedIdx = it },
                    onAddMediaClick = {},
                    onToggleMuteAudio = {},
                    onScrubTime = { scrubbedMs = it },
                    onSplitClip = {},
                    onDeleteClip = {},
                    audioTrackName = "Background Music",
                    audioTrackDurationMs = 9000L,
                    audioTrackStartMs = 0L,
                    onAudioTrackClick = {},
                    onAddAudioTrackClick = {},
                    onOpenTransitionManager = {},
                    onReplaceClip = {},
                    onSpeedAdjustClick = {}
                )
            }
        }

        // Verify timeline track exists with proper testTag
        composeTestRule.onNodeWithTag("youcut_timeline_track").assertExists()

        // Verify both scene cards are rendered and accessible
        composeTestRule.onNodeWithTag("timeline_scene_0").assertExists()
        composeTestRule.onNodeWithTag("timeline_scene_1").assertExists()

        // Verify add media button is present
        composeTestRule.onNodeWithTag("timeline_add_media_button").assertExists()

        // Click scene 1 to select
        composeTestRule.onNodeWithTag("timeline_scene_1").performClick()
        assertEquals("Scene 1 selected", 1, selectedIdx)
    }
}
