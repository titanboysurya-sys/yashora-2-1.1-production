package com.ritvyom.yashoraReelgenerator.presentation.utils

import androidx.compose.ui.graphics.Color

object EditorConstants {

    // 16 Video Styles
    val VIDEO_STYLES = listOf(
        StyleItem("Cinematic", "Epic anamorphic lighting & camera angles", "🎥"),
        StyleItem("Realistic", "High-detail life-like atmosphere", "📸"),
        StyleItem("Anime", "Hand-drawn vibrant Japanese anime cells", "🌸"),
        StyleItem("Cartoon", "Playful vectors & bold cartoon outlines", "🎨"),
        StyleItem("Oil Painting", "Rich textures and expressive heavy strokes", "🖼️"),
        StyleItem("Watercolor", "Fluid washes and bright dreamlike bleeds", "💧"),
        StyleItem("3D Animation", "Glossy clay textures with detailed models", "👾"),
        StyleItem("Pixar Style", "Warm character-driven cinematic charm", "🎈"),
        StyleItem("Cyberpunk", "Drenched in glowing neon lights and dark metals", "⚡"),
        StyleItem("Sci-Fi", "Futuristic spaceships and holograms", "🚀"),
        StyleItem("Documentary", "Raw archival footage with natural grain", "🎙️"),
        StyleItem("Storytelling", "Warm storybook textures and soft focuses", "📚"),
        StyleItem("Minimalist", "Sleek lines and clean balanced negative space", "▫️"),
        StyleItem("Vintage", "Scratchy 8mm film with nostalgic light leaks", "💾"),
        StyleItem("Fantasy", "Magic embers and ethereal fairy dust atmospheres", "🦄"),
        StyleItem("Sketch Art", "Monochrome pencil graphite cross-hatches", "✏️")
    )

    // 18 Languages
    val LANGUAGES = listOf(
        "English", "Hindi", "Arabic", "Urdu", "Bengali", "Tamil",
        "Telugu", "Japanese", "Chinese", "Korean", "Russian",
        "German", "French", "Spanish", "Portuguese", "Turkish",
        "Indonesian", "Thai", "Vietnamese"
    )

    // Background music categories
    val MUSIC_CATEGORIES = listOf(
        MusicItem("Cinematic", "Orchestral violins and epic deep drums", "🎻"),
        MusicItem("Motivational", "Plucky acoustics and energetic rises", "🎸"),
        MusicItem("Emotional", "Soft ambient piano chords", "🎹"),
        MusicItem("Technology", "Synthesized synthwave pulses", "💻"),
        MusicItem("Horror", "Dissonant pads and creepy ambient textures", "👻"),
        MusicItem("Happy", "Upbeat ukulele and playful whistles", "☀️"),
        MusicItem("Documentary", "Subtle organic textures and guitar thrums", "🌍"),
        MusicItem("News", "Authoritative broadcast brass and deep chimes", "📰"),
        MusicItem("Inspirational", "Swells, bells, and majestic delayed guitars", "🌟")
    )

    // Non-redundant curated distinct voices
    val MALE_VOICES = listOf(
        "Professional Man", "Deep Narrator", "Bold Dynamic", "Young Boy", "Old Grandpa"
    )
    val FEMALE_VOICES = listOf(
        "Professional Woman", "Soft Ambient", "Energetic Girl", "Old Grandma", "Storyteller"
    )
    val CHILD_VOICES = listOf(
        "Baby Boy", "Sweet Kid", "Playful Child", "Cute Little Girl"
    )

    // Audio transition types
    val TRANSITIONS = listOf("None", "Fade", "Zoom", "Slide", "Blur", "Cinematic")

    // Filter list: 50+ beautiful unique creative filters for high aesthetic visuals!
    val FILTERS = listOf(
        FilterItem("Normal", Color(0x00000000)),
        FilterItem("Vaporwave", Color(0x3FFF007F)),
        FilterItem("Golden Hour", Color(0x3FFFF9B0)),
        FilterItem("Sepia", Color(0x4F704214)),
        FilterItem("Noir", Color(0x8F0D0D0D)),
        FilterItem("Emerald", Color(0x2F00FF66)),
        FilterItem("Cobalt", Color(0x2F0033FF)),
        FilterItem("Crimson", Color(0x2FFF0033)),
        FilterItem("Warm Sunset", Color(0x3FFFA500)),
        FilterItem("Cool Indigo", Color(0x3F4B0082)),
        FilterItem("Cyberpunk Pink", Color(0x3FFF00FF)),
        FilterItem("Sleek Silver", Color(0x2FCCCCCC)),
        FilterItem("Toxic Green", Color(0x2F39FF14)),
        FilterItem("Plum", Color(0x3F800080)),
        FilterItem("Aqua Marine", Color(0x2F00FFFF)),
        FilterItem("Desert Gold", Color(0x3FEEDC82)),
        FilterItem("Mint Breeze", Color(0x2F98FF98)),
        FilterItem("Lavender Mist", Color(0x2FE6E6FA)),
        FilterItem("Peach Fizz", Color(0x2FFFFDAB)),
        FilterItem("Crimson Glow", Color(0x3FFC0C30)),
        FilterItem("Chroma Blue", Color(0x2F0018A8)),
        FilterItem("Olive Drab", Color(0x2F556B2F)),
        FilterItem("Burgundy", Color(0x3F800020)),
        FilterItem("Steel Grey", Color(0x2F708090)),
        FilterItem("Sunny Honey", Color(0x2FFFFF00)),
        FilterItem("Fairy Dust", Color(0x3FFDE910)),
        FilterItem("Deep Velvet", Color(0x3F1A0216)),
        FilterItem("Teal Lagoon", Color(0x2F008080)),
        FilterItem("Coral Reef", Color(0x3FFF7F50)),
        FilterItem("Amber Tint", Color(0x2FFFBF00)),
        FilterItem("Sky Azure", Color(0x2F007FFF)),
        FilterItem("Orchid", Color(0x2FDA70D6)),
        FilterItem("Lime Soda", Color(0x2F00FF00)),
        FilterItem("Salmon Warm", Color(0x2FFA07A0)),
        FilterItem("Charcoal Dark", Color(0x5F151515)),
        FilterItem("Royal Gold", Color(0x3FD4AF37)),
        FilterItem("Copper Burn", Color(0x3FB87333)),
        FilterItem("Electric Purple", Color(0x3F8B00FF)),
        FilterItem("Ocean Breeze", Color(0x2F008080)),
        FilterItem("Vintage Ochre", Color(0x3FCC7722)),
        FilterItem("Midnight Blue", Color(0x3F191970)),
        FilterItem("Grapevine", Color(0x3F6F2DA8)),
        FilterItem("Rose Petal", Color(0x2FFF007F)),
        FilterItem("Autumn Leaf", Color(0x3FE35335)),
        FilterItem("Forest Canopy", Color(0x2F228B22)),
        FilterItem("Neon Radioactive", Color(0x3FFFDF00)),
        FilterItem("Glume Gold", Color(0x2FE5E4E2)),
        FilterItem("Sage Green", Color(0x2F87A96B)),
        FilterItem("Polarized", Color(0x4F0F4C81)),
        FilterItem("Retro CRT", Color(0x2FFAFAF0)),
        FilterItem("Vivid Magenta", Color(0x3FFF00FF))
    )

    // Sticker presets (Emoji list)
    val STICKERS = listOf(
        "🔥", "✨", "👑", "🎬", "🚀", "💡", "💖", "🎧", "⚡", "🌟",
        "🎯", "🎵", "💬", "📺", "🎉", "🔥", "📣", "❤️", "😍", "😂"
    )

    // Font styles
    val FONTS = listOf("Sans-Serif", "Serif", "Monospace", "Display Bold", "TikTok Style", "Insta Premium")

    // Video Category Presets (Automatically configures voice, pitch, speed, emotion state & bg music)
    val VOICE_PRESETS = listOf(
        VoicePreset("Motivation", "🔥", "Fast, deep & powerful dynamic sound", "Male", "Bold Dynamic", 1.15f, 1.0f, "Energetic", "Motivational"),
        VoicePreset("News & Facts", "🎙️", "Clear, formal authoritative narration", "Male", "Professional Man", 1.00f, 0.95f, "Friendly", "News"),
        VoicePreset("Kids Story", "🧒", "High-pitch animated warm storyteller", "Child", "Cute Little Girl", 1.02f, 1.45f, "Friendly", "Happy"),
        VoicePreset("Elderly Wisdom", "👴", "Slow, warm, experienced grandpa voice", "Male", "Old Grandpa", 0.85f, 0.80f, "Cinematic", "Inspirational"),
        VoicePreset("Emotional Drama", "📖", "Soft, expressive storytelling female tone", "Female", "Storyteller", 0.90f, 1.02f, "Emotional", "Emotional"),
        VoicePreset("Peaceful Zen", "🧘", "Calm, slow, whispering spiritual cadence", "Female", "Soft Ambient", 0.88f, 1.05f, "Cinematic", "Inspirational")
    )

    // Cinematic or Social-Media Publishing Styles
    val SOCIAL_AND_CINEMATIC_PUBLISHING_STYLES = listOf(
        PublishingStyleItem("TikTok / Instagram Reels", "High energy, dynamic cuts, high-impact hooks for short vertical feeds", "📱"),
        PublishingStyleItem("YouTube Short", "Retention-optimized storytelling with clear focus points", "🔴"),
        PublishingStyleItem("Cinematic Vlog / Trailer", "Dramatic wide-scenic narratives and slower transitions", "🎭"),
        PublishingStyleItem("Short Documentary", "Historical, photojournalistic, and authoritative analytical flow", "🏛️"),
        PublishingStyleItem("Facebook Feed Video", "Highly engaging attention grabber, viral or structured lifestyle format", "👥"),
        PublishingStyleItem("LinkedIn Professional", "Sleek, polished, clean business and tech authority updates", "💼"),
        PublishingStyleItem("Ad Promo / Marketing", "Commercial product highlights, conversions-driven hooks and CTAs", "🎯")
    )
}

data class PublishingStyleItem(val name: String, val description: String, val icon: String)

data class VoicePreset(
    val name: String,
    val icon: String,
    val subtitle: String,
    val voiceCategory: String,
    val voiceName: String,
    val speed: Float,
    val pitch: Float,
    val emotion: String,
    val musicCategory: String
)

data class StyleItem(val name: String, val description: String, val icon: String)
data class MusicItem(val name: String, val description: String, val icon: String)
data class FilterItem(val name: String, val tintColor: Color)
