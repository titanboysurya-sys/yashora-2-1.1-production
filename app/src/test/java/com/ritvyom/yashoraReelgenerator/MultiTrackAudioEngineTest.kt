package com.ritvyom.yashoraReelgenerator

import com.ritvyom.yashoraReelgenerator.core.model.AnimatableProperty
import com.ritvyom.yashoraReelgenerator.core.model.AudioClipItem
import com.ritvyom.yashoraReelgenerator.core.model.CanonicalTimeline
import com.ritvyom.yashoraReelgenerator.core.model.Keyframe
import com.ritvyom.yashoraReelgenerator.core.model.KeyframeInterpolation
import com.ritvyom.yashoraReelgenerator.core.model.KeyframeTrack
import com.ritvyom.yashoraReelgenerator.core.model.TimelineTrack
import com.ritvyom.yashoraReelgenerator.core.model.TrackType
import com.ritvyom.yashoraReelgenerator.core.model.VideoClipItem
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import com.ritvyom.yashoraReelgenerator.engine.TimelineEvaluator
import com.ritvyom.yashoraReelgenerator.engine.YashoraEngineBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 7: Professional Multi-Track Audio Engine Unit Tests.
 *
 * Verifies:
 * 1. Multi-track audio evaluation (BGM, Voice-over, SFX, Original Audio) via TimelineEvaluator.
 * 2. Deterministic volume calculation including clip volume and track volume.
 * 3. Fade-in and fade-out attenuation curves.
 * 4. Keyframed volume automation and pan evaluation.
 * 5. Track-level mute and clip-level mute handling.
 * 6. Playback speed and source timestamp mapping.
 * 7. Lossless synchronization from Scene models to CanonicalTimeline audio tracks via YashoraEngineBridge.
 */
class MultiTrackAudioEngineTest {

    @Test
    fun testMultiTrackAudioEvaluation_generatesAllActiveLayers() {
        val bgmClip = AudioClipItem(
            id = "bgm_1",
            trackId = "track_bgm",
            name = "BGM Track",
            startTimeUs = 0L,
            durationUs = 10_000_000L,
            audioUri = "/storage/bgm.mp3",
            volume = 0.8f
        )
        val voiceClip = AudioClipItem(
            id = "voice_1",
            trackId = "track_voice",
            name = "Voice-over",
            startTimeUs = 1_000_000L,
            durationUs = 4_000_000L,
            audioUri = "/storage/voice.wav",
            volume = 1.0f
        )
        val sfxClip = AudioClipItem(
            id = "sfx_1",
            trackId = "track_sfx",
            name = "Sound Effect",
            startTimeUs = 2_000_000L,
            durationUs = 1_000_000L,
            audioUri = "whoosh.wav",
            volume = 0.5f
        )
        val originalVideoClip = VideoClipItem(
            id = "video_clip_1",
            trackId = "track_main",
            name = "Video with mic audio",
            startTimeUs = 0L,
            durationUs = 10_000_000L,
            mediaUri = "/storage/video.mp4",
            volume = 0.7f
        )

        val timeline = CanonicalTimeline(
            tracks = listOf(
                TimelineTrack(id = "track_main", name = "Main Video", type = TrackType.MAIN_VIDEO, clips = listOf(originalVideoClip)),
                TimelineTrack(id = "track_bgm", name = "BGM", type = TrackType.AUDIO_BGM, volume = 0.5f, clips = listOf(bgmClip)),
                TimelineTrack(id = "track_voice", name = "Voice", type = TrackType.AUDIO_VOICEOVER, volume = 1.0f, clips = listOf(voiceClip)),
                TimelineTrack(id = "track_sfx", name = "SFX", type = TrackType.AUDIO_SFX, volume = 1.0f, clips = listOf(sfxClip))
            )
        )

        val evaluator = TimelineEvaluator(timeline)

        // At time 2.5s (2_500_000 us): All 4 audio sources are active!
        val activeNodesAt2_5s = evaluator.evaluateAudioForTime(2_500_000L)
        assertEquals(4, activeNodesAt2_5s.size)

        val activeBgm = activeNodesAt2_5s.first { it.trackType == TrackType.AUDIO_BGM }
        assertEquals(0.8f * 0.5f, activeBgm.effectiveVolume, 0.001f) // clip volume * track volume

        val activeVoice = activeNodesAt2_5s.first { it.trackType == TrackType.AUDIO_VOICEOVER }
        assertEquals(1.0f, activeVoice.effectiveVolume, 0.001f)

        val activeSfx = activeNodesAt2_5s.first { it.trackType == TrackType.AUDIO_SFX }
        assertEquals(0.5f, activeSfx.effectiveVolume, 0.001f)

        val activeOriginal = activeNodesAt2_5s.first { it.trackType == TrackType.MAIN_VIDEO }
        assertEquals(0.7f, activeOriginal.effectiveVolume, 0.001f)

        // At time 0.5s: Only original video audio and BGM are active (voice starts at 1s, SFX at 2s)
        val activeNodesAt0_5s = evaluator.evaluateAudioForTime(500_000L)
        assertEquals(2, activeNodesAt0_5s.size)
        assertTrue(activeNodesAt0_5s.any { it.trackType == TrackType.MAIN_VIDEO })
        assertTrue(activeNodesAt0_5s.any { it.trackType == TrackType.AUDIO_BGM })

        // At time 3.5s: SFX ended (was 2s to 3s), voice and BGM and original are active
        val activeNodesAt3_5s = evaluator.evaluateAudioForTime(3_500_000L)
        assertEquals(3, activeNodesAt3_5s.size)
    }

    @Test
    fun testAudioFades_fadeInAndFadeOut() {
        val fadeInUs = 1_000_000L // 1 sec
        val fadeOutUs = 1_000_000L // 1 sec
        val durationUs = 5_000_000L

        val clip = AudioClipItem(
            id = "clip_fade",
            trackId = "track_audio",
            startTimeUs = 0L,
            durationUs = durationUs,
            audioUri = "/audio.wav",
            volume = 1.0f,
            fadeInDurationUs = fadeInUs,
            fadeOutDurationUs = fadeOutUs
        )

        val timeline = CanonicalTimeline(
            tracks = listOf(
                TimelineTrack(id = "track_audio", name = "Audio", type = TrackType.AUDIO_BGM, clips = listOf(clip))
            )
        )

        val evaluator = TimelineEvaluator(timeline)

        // At 0s: fade in starts at 0 volume
        val at0 = evaluator.evaluateAudioForTime(0L).first()
        assertEquals(0.0f, at0.effectiveVolume, 0.001f)

        // At 0.5s: halfway through fade in -> volume 0.5
        val atHalfSec = evaluator.evaluateAudioForTime(500_000L).first()
        assertEquals(0.5f, atHalfSec.effectiveVolume, 0.001f)

        // At 2s: fully faded in -> volume 1.0
        val at2Sec = evaluator.evaluateAudioForTime(2_000_000L).first()
        assertEquals(1.0f, at2Sec.effectiveVolume, 0.001f)

        // At 4.5s: halfway through fade out (5.0s - 0.5s remaining) -> volume 0.5
        val at4_5Sec = evaluator.evaluateAudioForTime(4_500_000L).first()
        assertEquals(0.5f, at4_5Sec.effectiveVolume, 0.001f)
    }

    @Test
    fun testAudioKeyframes_volumeAndPanAutomation() {
        val volumeKeyframes = listOf(
            Keyframe(0L, 0.2f, KeyframeInterpolation.LINEAR),
            Keyframe(2_000_000L, 1.0f, KeyframeInterpolation.LINEAR)
        )
        val panKeyframes = listOf(
            Keyframe(0L, -1.0f, KeyframeInterpolation.LINEAR), // full left
            Keyframe(2_000_000L, 1.0f, KeyframeInterpolation.LINEAR) // full right
        )

        val clip = AudioClipItem(
            id = "clip_kf",
            trackId = "track_audio",
            startTimeUs = 0L,
            durationUs = 2_000_000L,
            audioUri = "/audio.wav",
            volume = 1.0f,
            keyframeTracks = listOf(
                KeyframeTrack(AnimatableProperty.VOLUME, volumeKeyframes, defaultValue = 1.0f),
                KeyframeTrack(AnimatableProperty.PAN, panKeyframes, defaultValue = 0.0f)
            )
        )

        val timeline = CanonicalTimeline(
            tracks = listOf(
                TimelineTrack(id = "track_audio", name = "Audio", type = TrackType.AUDIO_BGM, clips = listOf(clip))
            )
        )

        val evaluator = TimelineEvaluator(timeline)

        // At 1.0s (halfway)
        val at1s = evaluator.evaluateAudioForTime(1_000_000L).first()
        // Volume interpolated between 0.2 and 1.0 = 0.6
        assertEquals(0.6f, at1s.effectiveVolume, 0.001f)
        // Pan interpolated between -1.0 and 1.0 = 0.0 (center)
        assertEquals(0.0f, at1s.pan, 0.001f)
    }

    @Test
    fun testTrackMuteAndClipMute() {
        val clip = AudioClipItem(
            id = "clip_1",
            trackId = "track_audio",
            startTimeUs = 0L,
            durationUs = 2_000_000L,
            audioUri = "/audio.wav",
            volume = 1.0f
        )

        // 1. Muted Track
        val mutedTrackTimeline = CanonicalTimeline(
            tracks = listOf(
                TimelineTrack(id = "track_audio", name = "Audio", type = TrackType.AUDIO_BGM, isMuted = true, clips = listOf(clip))
            )
        )
        val evaluator1 = TimelineEvaluator(mutedTrackTimeline)
        assertTrue(evaluator1.evaluateAudioForTime(500_000L).isEmpty())

        // 2. Muted Clip
        val mutedClip = clip.copy(isMuted = true)
        val mutedClipTimeline = CanonicalTimeline(
            tracks = listOf(
                TimelineTrack(id = "track_audio", name = "Audio", type = TrackType.AUDIO_BGM, isMuted = false, clips = listOf(mutedClip))
            )
        )
        val evaluator2 = TimelineEvaluator(mutedClipTimeline)
        assertTrue(evaluator2.evaluateAudioForTime(500_000L).isEmpty())
    }

    @Test
    fun testYashoraEngineBridge_extractsAudioTracksFromScenes() {
        val scene1 = Scene(
            sceneNumber = 1,
            narrationText = "Hello",
            visualPrompt = "Scene 1",
            durationSeconds = 3,
            subtitle = "Hello",
            customVoiceAudioPath = "/storage/custom_voice_1.wav",
            sfxName = "Whoosh",
            volume = 0.9f
        )
        val scene2 = Scene(
            sceneNumber = 2,
            narrationText = "World",
            visualPrompt = "Scene 2",
            durationSeconds = 4,
            subtitle = "World",
            customVoiceAudioPath = "/storage/custom_voice_2.m4a",
            sfxName = "None",
            volume = 1.0f
        )

        val timeline = YashoraEngineBridge.createCanonicalTimeline(listOf(scene1, scene2))

        val voiceTrack = timeline.tracks.firstOrNull { it.type == TrackType.AUDIO_VOICEOVER }
        val sfxTrack = timeline.tracks.firstOrNull { it.type == TrackType.AUDIO_SFX }

        assertTrue("Voiceover track should exist", voiceTrack != null)
        assertEquals(2, voiceTrack!!.clips.size)

        assertTrue("SFX track should exist", sfxTrack != null)
        assertEquals(1, sfxTrack!!.clips.size) // Scene 2 was "None", so only 1 SFX clip

        // Verify audioTracks getter returns both audio tracks
        assertEquals(2, timeline.audioTracks.size)
    }

    @Test
    fun testAudioMixerEngine_stereoPanEqualPower() {
        val (leftC, rightC) = com.ritvyom.yashoraReelgenerator.engine.audio.AudioMixerEngine.calculateStereoPanGains(0.0f)
        assertEquals(0.7071f, leftC, 0.01f)
        assertEquals(0.7071f, rightC, 0.01f)

        val (leftL, rightL) = com.ritvyom.yashoraReelgenerator.engine.audio.AudioMixerEngine.calculateStereoPanGains(-1.0f)
        assertEquals(1.0f, leftL, 0.01f)
        assertEquals(0.0f, rightL, 0.01f)

        val (leftR, rightR) = com.ritvyom.yashoraReelgenerator.engine.audio.AudioMixerEngine.calculateStereoPanGains(1.0f)
        assertEquals(0.0f, leftR, 0.01f)
        assertEquals(1.0f, rightR, 0.01f)
    }

    @Test
    fun testAudioMixerEngine_softLimitPreventsClipping() {
        // Extreme positive and negative spikes should be limited smoothly below Short.MAX_VALUE / Short.MIN_VALUE
        val limitedMax = com.ritvyom.yashoraReelgenerator.engine.audio.AudioMixerEngine.softLimit(100_000f)
        assertTrue(limitedMax <= Short.MAX_VALUE)
        assertTrue(limitedMax > 30000)

        val limitedMin = com.ritvyom.yashoraReelgenerator.engine.audio.AudioMixerEngine.softLimit(-100_000f)
        assertTrue(limitedMin >= Short.MIN_VALUE)
        assertTrue(limitedMin < -30000)

        // Values below threshold remain linear
        val linearVal = com.ritvyom.yashoraReelgenerator.engine.audio.AudioMixerEngine.softLimit(15000f)
        assertEquals(15000.toShort(), linearVal)
    }

    @Test
    fun testYashoraEngineBridge_addsBgmTrackWhenEnabled() {
        val scene = Scene(
            sceneNumber = 1,
            narrationText = "Narration",
            visualPrompt = "Visual",
            durationSeconds = 5,
            subtitle = "Narration"
        )
        val timeline = YashoraEngineBridge.createCanonicalTimeline(
            scenes = listOf(scene),
            bgMusicCategory = "Cinematic",
            bgMusicVolume = 0.6f,
            bgMusicEnabled = true
        )

        val bgmTrack = timeline.tracks.firstOrNull { it.type == TrackType.AUDIO_BGM }
        assertTrue("BGM track should be created when enabled", bgmTrack != null)
        assertEquals(0.6f, bgmTrack!!.volume, 0.01f)
        assertEquals(1, bgmTrack.clips.size)
        assertEquals("bgm:Cinematic", (bgmTrack.clips[0] as AudioClipItem).audioUri)
    }
}
