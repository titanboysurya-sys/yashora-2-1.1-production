package com.ritvyom.yashoraReelgenerator.presentation.utils

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ritvyom.yashoraReelgenerator.domain.models.Scene
import kotlin.random.Random

// Filter Category Definition
data class FilterData(
    val name: String,
    val category: String,
    val colorFilterMatrix: ColorMatrix?,
    val tintOverlayColor: Color = Color.Transparent
)

// Effect Definition
data class EffectData(
    val name: String,
    val category: String,
    val description: String
)

object FilterManager {
    val CATEGORIES = listOf(
        "Trending", "Cinematic", "Dreamy", "Retro", "Vintage",
        "B&W", "Pastel", "Film", "HDR", "Warm", "Cool"
    )

    val ALL_FILTERS = listOf(
        // Trending
        FilterData("Glitz", "Trending", createSaturationMatrix(1.5f), Color(0x2FFF007F)),
        FilterData("Teal Gold", "Trending", createTealOrangeMatrix(), Color(0x1FFF7F00)),
        
        // Cinematic
        FilterData("Cinematic Teal", "Cinematic", createTealOrangeMatrix(), Color(0x2F00E5FF)),
        FilterData("Classic Drama", "Cinematic", createContrastMatrix(1.3f), Color(0x1F000000)),
        FilterData("Blockbuster", "Cinematic", createCinemaMatrix(), Color(0x1F228B22)),

        // Dreamy
        FilterData("Dreamy Peach", "Dreamy", createContrastMatrix(0.85f), Color(0x3FFFCCAA)),
        FilterData("Mist Velvet", "Dreamy", createContrastMatrix(0.9f), Color(0x2F8A2BE2)),

        // Retro
        FilterData("Retro 90s", "Retro", createWarmOchMatrix(), Color(0x3FFFAA00)),
        FilterData("VHS Tint", "Retro", createSepiaMatrix(), Color(0x2F00FF66)),

        // Vintage
        FilterData("Vintage Sepia", "Vintage", createSepiaMatrix(), Color(0x3F704214)),
        FilterData("Old Photo", "Vintage", createSepiaMatrix(), Color(0x4F8B5A2B)),

        // B&W
        FilterData("Noir Dark", "B&W", createSaturationMatrix(0f), Color(0x2F0D0D0D)),
        FilterData("High Contrast Grey", "B&W", createBwHighContrastMatrix(), Color.Transparent),

        // Pastel
        FilterData("Soft Pastel", "Pastel", createPastelMatrix(), Color(0x2F98FF98)),
        FilterData("Baby Blush", "Pastel", createPastelMatrix(), Color(0x2FFFFDAB)),

        // Film
        FilterData("Interstellar", "Film", createContrastMatrix(1.25f), Color(0x2F0F4C81)),
        FilterData("Kodak Matte", "Film", createSaturationMatrix(0.9f), Color(0x1F800000)),

        // HDR
        FilterData("HDR Enhanced", "HDR", createHdrMatrix(), Color.Transparent),
        FilterData("Clarity Boost", "HDR", createContrastMatrix(1.4f), Color.Transparent),

        // Warm
        FilterData("Warm Sunset", "Warm", createSaturationMatrix(1.1f), Color(0x3FFF7F00)),
        FilterData("Sunny Field", "Warm", createSaturationMatrix(1.2f), Color(0x3FFFFD1A)),

        // Cool
        FilterData("Ice Castle", "Cool", createSaturationMatrix(0.95f), Color(0x3F00BFFF)),
        FilterData("Indigo Night", "Cool", createContrastMatrix(1.1f), Color(0x3F4B0082))
    )

    fun getFiltersByCategory(category: String): List<FilterData> {
        return ALL_FILTERS.filter { it.category.equals(category, ignoreCase = true) }
    }

    private fun createSaturationMatrix(saturation: Float): ColorMatrix {
        return ColorMatrix().apply { setToSaturation(saturation) }
    }

    private fun createContrastMatrix(contrast: Float): ColorMatrix {
        val scale = contrast
        val translate = 128f * (1f - scale)
        return ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun createSepiaMatrix(): ColorMatrix {
        return ColorMatrix(floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun createTealOrangeMatrix(): ColorMatrix {
        return ColorMatrix(floatArrayOf(
            1.15f, 0f, 0f, 0f, 15f,
            0f, 0.9f, 0.1f, 0f, -10f,
            0f, 0f, 1.25f, 0f, 25f,
            0f, 0f, 0f, 0.95f, 0f
        ))
    }

    private fun createCinemaMatrix(): ColorMatrix {
        return ColorMatrix(floatArrayOf(
            1.2f, 0f, -0.1f, 0f, 5f,
            -0.1f, 1.15f, 0f, 0f, 10f,
            -0.1f, 0f, 1.3f, 0f, 15f,
            0f, 0f, 0f, 1.0f, 0f
        ))
    }

    private fun createBwHighContrastMatrix(): ColorMatrix {
        return ColorMatrix(floatArrayOf(
            1.5f, 1.5f, 1.5f, 0f, -150f,
            1.5f, 1.5f, 1.5f, 0f, -150f,
            1.5f, 1.5f, 1.5f, 0f, -150f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun createPastelMatrix(): ColorMatrix {
        return ColorMatrix(floatArrayOf(
            0.85f, 0.1f, 0.1f, 0f, 35f,
            0.1f, 0.85f, 0.1f, 0f, 35f,
            0.1f, 0.1f, 0.85f, 0f, 35f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun createWarmOchMatrix(): ColorMatrix {
        return ColorMatrix(floatArrayOf(
            1.2f, 0.1f, 0f, 0f, 20f,
            0.1f, 1.05f, 0f, 0f, 10f,
            0f, 0.05f, 0.9f, 0f, -15f,
            0f, 0f, 0f, 1f, 0f
        ))
    }

    private fun createHdrMatrix(): ColorMatrix {
        return ColorMatrix(floatArrayOf(
            1.35f, -0.1f, -0.1f, 0f, 10f,
            -0.1f, 1.35f, -0.1f, 0f, 10f,
            -0.1f, -0.1f, 1.35f, 0f, 10f,
            0f, 0f, 0f, 1f, 0f
        ))
    }
}

object EffectManager {
    val CATEGORIES = listOf("Basic", "Party", "Glitch", "Nature", "Cinematic", "Spark ✨", "Fire 🔥", "Lens Flare ☀", "Stickers 😊")

    val ALL_EFFECTS = listOf(
        // Basic
        EffectData("Blur", "Basic", "Gaussian focal softening"),
        EffectData("Glow", "Basic", "Radiant neon outlines"),
        EffectData("Sharpen", "Basic", "Extreme detail extraction"),
        EffectData("Motion Blur", "Basic", "Synthesized shutter sweep"),

        // Party
        EffectData("Disco", "Party", "Pulsing multi-color light strokes"),
        EffectData("Spark", "Party", "Shining gold star sparkles"),
        EffectData("Flash", "Party", "High-exposure strobe beats"),
        EffectData("RGB Shift", "Party", "Chromatic visual split"),

        // Glitch
        EffectData("VHS", "Glitch", "Retro magnetic tape scanlines"),
        EffectData("TV Noise", "Glitch", "Salt-and-pepper CRT signal block"),
        EffectData("Chromatic", "Glitch", "Heavy split visual lag"),
        EffectData("Shake", "Glitch", "Jumping video frames"),

        // Nature
        EffectData("Snow", "Nature", "Drifting winter snow flakes"),
        EffectData("Rain", "Nature", "Cinematic storm rain fall"),
        EffectData("Dust", "Nature", "Atmospheric warm golden specs"),
        EffectData("Bokeh", "Nature", "Beautiful circular soft bubbles"),

        // Cinematic
        EffectData("Film Grain", "Cinematic", "Authentic 35mm silver grain"),
        EffectData("Lens Flare", "Cinematic", "Anamorphic horizon flare block"),
        EffectData("Light Leak", "Cinematic", "Dynamic warm orange leaks"),
        EffectData("Bloom", "Cinematic", "Exquisite radiant highlights"),

        // Spark ✨
        EffectData("Neon Sparks", "Spark ✨", "Pulsing shiny golden vector stars floating in motion"),
        EffectData("Gold Sparklers", "Spark ✨", "Sparkling trails expanding from the screen center"),
        EffectData("Fireflies Blast", "Spark ✨", "Magical bioluminescent neon specs floating slowly"),

        // Fire 🔥
        EffectData("Volcanic Fireball", "Fire 🔥", "Warm glowing plasma fireball particles rising with gradients"),
        EffectData("Campfire Flame", "Fire 🔥", "Flickering campfire elements floating bottom-up"),
        EffectData("Ember Sparks", "Fire 🔥", "High-intensity rising red-hot carbon embers"),

        // Lens Flare ☀
        EffectData("Sunset Flare Glow", "Lens Flare ☀", "Warm horizontal golden streak with centered glow rings"),
        EffectData("Cinematic Blue", "Lens Flare ☀", "Anamorphic cyberic blue horizontal flare line with halos"),
        EffectData("Anamorphic Gold", "Lens Flare ☀", "Cinematic heavy golden leak overlay with hexagonal flare nodes"),

        // Stickers 😊
        EffectData("Pulsing Heart", "Stickers 😊", "A beautifully rendered smooth heart icon pulsing perfectly"),
        EffectData("Fire Flame Group", "Stickers 😊", "Vibrant flickering neon fire icons jumping and expanding"),
        EffectData("Thumbs Up Pulse", "Stickers 😊", "Animated social thumb up icon bouncing dynamically"),
        EffectData("Arrow Point Down", "Stickers 😊", "Neon arrow bouncing pointing down to content"),
        EffectData("Subscribe Overlay", "Stickers 😊", "Pulsing CapCut-style SUBSCRIBE button overlay")
    )

    fun getEffectsByCategory(category: String): List<EffectData> {
        return ALL_EFFECTS.filter { it.category.equals(category, ignoreCase = true) }
    }
}

// Shader and Visual Simulation Render Pipeline
object RenderPipeline {

    @Composable
    fun applyEffects(
        effectName: String,
        intensity: Int,
        isPlaying: Boolean,
        modifier: Modifier = Modifier,
        scene: Scene? = null
    ): Modifier {
        if (effectName == "None" || intensity == 0) return modifier

        val factor = intensity / 100f
        var localModifier = modifier

        // 1. Compose Pre-Render Filters (Blur, Transform Layers)
        when (effectName) {
            "Blur" -> {
                localModifier = localModifier.blur((20f * factor).dp)
            }
            "Motion Blur" -> {
                localModifier = localModifier.blur((12f * factor).dp)
            }
        }

        // Animated states for real-time video simulation effects
        val infiniteTransition = rememberInfiniteTransition(label = "FX")
        
        val timeValue by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ), label = "Time"
        )

        val flashValue by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "Flash"
        )

        val noiseRandom = remember { mutableStateListOf(0f, 0f, 0f) }
        LaunchedEffect(timeValue) {
            if (isPlaying) {
                noiseRandom[0] = Random.nextFloat()
                noiseRandom[1] = Random.nextFloat()
                noiseRandom[2] = Random.nextFloat()
            }
        }

        // 2. Add Layer-By-Layer Rendering Engine (using drawWithContent & GraphicsLayer)
        localModifier = localModifier.graphicsLayer {
            if (isPlaying) {
                when (effectName) {
                    "Shake" -> {
                        translationX = (Random.nextInt(-15, 15) * factor)
                        translationY = (Random.nextInt(-15, 15) * factor)
                    }
                    "RGB Shift" -> {
                        scaleX = 1f + (0.04f * factor)
                        scaleY = 1f + (0.04f * factor)
                    }
                    else -> {}
                }
            }
        }.drawWithContent {
            // Render original clip
            drawContent()

            // Apply procedural effects
            val pFactor = factor
            val t = timeValue
            val rPulse = if (isPlaying) noiseRandom[0] else 0.5f

            when (effectName) {
                "Glow", "Bloom" -> {
                    // Radiant glow overlay
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.35f * pFactor), Color.Transparent),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width * 1.2f
                        )
                    )
                }

                "Disco" -> {
                    // Pulsing RGB disco colors
                    val cycleColor = when {
                        t < 0.25f -> Color.Red
                        t < 0.50f -> Color.Green
                        t < 0.75f -> Color.Blue
                        else -> Color(0xFFFF007F)
                    }
                    drawRect(
                        color = cycleColor.copy(alpha = 0.23f * pFactor)
                    )
                }

                "Flash" -> {
                    // Strobe pulsing
                    val burstAlpha = if (flashValue > 0.7f && isPlaying) 0.5f * pFactor else 0f
                    drawRect(
                        color = Color.White.copy(alpha = burstAlpha)
                    )
                }

                "TV Noise" -> {
                    // Procedural grey grain
                    val greyVal = 0.15f * pFactor * rPulse
                    drawRect(
                        color = Color.White.copy(alpha = greyVal)
                    )
                    // Draw scan line grid
                    val lines = 35
                    val step = size.height / lines
                    for (i in 0 until lines) {
                        val y = i * step + (t * step)
                        drawLine(
                            color = Color.Black.copy(alpha = 0.2f * pFactor),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                "VHS" -> {
                    // Warm nostalgic tape look with static bottom interference bar
                    drawRect(
                        color = Color(0xFF6B5B95).copy(alpha = 0.15f * pFactor)
                    )
                    // Static scan lines
                    val linesCount = 18
                    val rowStep = size.height / linesCount
                    for (i in 0 until linesCount) {
                        val y = i * rowStep + ((t * 0.5f) * size.height)
                        drawLine(
                            color = Color.White.copy(alpha = 0.12f * pFactor),
                            start = Offset(0f, y % size.height),
                            end = Offset(size.width, y % size.height),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                }

                "Snow" -> {
                    // Real-time customizable dynamic particle system
                    val snowflakesCount = scene?.snowFlakeCount ?: 40
                    val speed = scene?.snowSpeed ?: 1.0f
                    val wind = scene?.snowWindDirection ?: 0.1f
                    
                    for (i in 0 until snowflakesCount) {
                        // Deterministic pseudo-random inputs for each index
                        val seedX = ((i * 31.17f) % 1f)
                        val seedY = ((i * 17.43f) % 1f)
                        val seedSize = ((i * 43.19f) % 1f)
                        
                        val progressY = (t * speed + seedY) % 1f
                        val y = size.height * progressY
                        
                        // Drift with sinusoidal wave fluttering and wrap horizontally
                        val windOffset = wind * size.width * progressY
                        val flutter = 15f * kotlin.math.sin(t * 2 * kotlin.math.PI + i).toFloat()
                        val x = (size.width * seedX + windOffset + flutter + size.width) % size.width
                        
                        val radius = (1.5f.dp + (3f * seedSize).dp).toPx()
                        drawCircle(
                            color = Color.White.copy(alpha = (0.3f + 0.6f * seedSize) * pFactor),
                            radius = radius,
                            center = Offset(x, y)
                        )
                    }
                }

                "Rain" -> {
                    // Real-time customizable storm rain droplets
                    val rainDropsCount = scene?.rainDropCount ?: 50
                    val speed = scene?.rainSpeed ?: 1.0f
                    val wind = scene?.rainWindDirection ?: -0.2f
                    
                    for (i in 0 until rainDropsCount) {
                        val seedX = ((i * 47.19f) % 1f)
                        val seedY = ((i * 19.33f) % 1f)
                        val seedSize = ((i * 29.81f) % 1f)
                        
                        val progressY = (t * speed + seedY) % 1f
                        val y = size.height * progressY
                        
                        val windOffset = wind * size.width * progressY
                        val x = (size.width * seedX + windOffset + size.width) % size.width
                        
                        // Length and slope of rain dropping lines
                        val length = (8f.dp + (14f * seedSize).dp).toPx()
                        val dx = wind * length
                        val dy = length
                        
                        drawLine(
                            color = Color(0xFFE0F7FA).copy(alpha = (0.25f + 0.45f * seedSize) * pFactor),
                            start = Offset(x, y),
                            end = Offset(x + dx, y + dy),
                            strokeWidth = (0.8f.dp + (0.8f * seedSize).dp).toPx()
                        )
                    }
                }

                "Dust" -> {
                    // Golden floating dust specs
                    val specs = 15
                    for (i in 0 until specs) {
                        val x = (size.width * 0.5f + size.width * 0.45f * kotlin.math.sin(t * 2 * kotlin.math.PI + i).toFloat())
                        val y = (size.height * ((t + (i * 0.07f)) % 1f))
                        drawCircle(
                            color = Color(0xFFFFD54F).copy(alpha = 0.6f * pFactor),
                            radius = (1.5f.dp + (i % 2).dp).toPx(),
                            center = Offset(x, y)
                        )
                    }
                }

                "Bokeh" -> {
                    // Soft shifting circle lenses
                    val bubbleCount = 7
                    for (i in 0 until bubbleCount) {
                        val x = (size.width * ((0.15f * i + t * 0.3f) % 1f))
                        val y = size.height * (0.3f + 0.1f * (i % 4) + 0.15f * kotlin.math.sin(t * kotlin.math.PI).toFloat())
                        drawCircle(
                            color = Color(0x33FFB74D).copy(alpha = 0.25f * pFactor),
                            radius = (20.dp + (i * 8).dp).toPx(),
                            center = Offset(x, y)
                        )
                    }
                }

                "Film Grain" -> {
                    // Overlay salt and pepper grain
                    drawRect(
                        color = Color.Black.copy(alpha = 0.08f * pFactor * rPulse)
                    )
                    drawRect(
                        color = Color.White.copy(alpha = 0.08f * pFactor * (1f - rPulse))
                    )
                }

                "Lens Flare" -> {
                    // Horizon cinematic anamorphic glow line
                    val centerY = size.height * 0.45f
                    drawLine(
                        color = Color(0xFF00E5FF).copy(alpha = 0.65f * pFactor),
                        start = Offset(0f, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = (2.dp + (rPulse * 3f).dp).toPx()
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.35f * pFactor),
                        radius = size.width * 0.18f,
                        center = Offset(size.width * 0.4f, centerY)
                    )
                }

                "Light Leak" -> {
                    // Shifting red gold warm leakage
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF5722).copy(alpha = 0.38f * pFactor), Color.Transparent),
                            center = Offset(size.width * (0.1f + t * 0.2f), size.height * 0.1f),
                            radius = size.width * 0.8f
                        )
                    )
                }

                "Spark", "Neon Sparks" -> {
                    // Rotating four-pointed stars drifting across the frame
                    val starCount = 10
                    for (i in 0 until starCount) {
                        val life = ((t + i * 0.1f) % 1f)
                        val angle = (t * 360f + i * 45f) * (kotlin.math.PI / 180f).toFloat()
                        val rx = size.width * 0.15f * life + (i * 20f)
                        val x = (size.width * 0.5f + rx * kotlin.math.cos(angle)) % size.width
                        val y = (size.height * 0.4f + rx * kotlin.math.sin(angle)) % size.height
                        val sSize = (12.dp + (i % 2).dp * 6).toPx() * (1f - life)
                        if (sSize > 0) {
                            val starPath = androidx.compose.ui.graphics.Path().apply {
                                moveTo(x, y - sSize)
                                quadraticTo(x, y, x + sSize, y)
                                quadraticTo(x, y, x, y + sSize)
                                quadraticTo(x, y, x - sSize, y)
                                quadraticTo(x, y, x, y - sSize)
                            }
                            drawPath(starPath, Color(0xFFFFD700).copy(alpha = (1f - life) * pFactor))
                        }
                    }
                }

                "Gold Sparklers" -> {
                    // Expanding dynamic sparkling trails from screen center
                    val trails = 16
                    for (i in 0 until trails) {
                        val angle = (i * (360f / trails)) * (kotlin.math.PI / 180f).toFloat()
                        val speedFactor = 1f + (i % 3) * 0.4f
                        val dist = size.width * 0.4f * t * speedFactor
                        val x = size.width / 2 + dist * kotlin.math.cos(angle)
                        val y = size.height / 2 + dist * kotlin.math.sin(angle)
                        val sizeP = (4.dp).toPx() * (1f - t)
                        if (sizeP > 0) {
                            drawCircle(
                                color = Color(0xFFFFEA00).copy(alpha = (1f - t) * pFactor),
                                radius = sizeP,
                                center = Offset(x, y)
                            )
                        }
                    }
                }

                "Fireflies Blast" -> {
                    // Magical glowing floating specifications
                    val fireflies = 20
                    for (i in 0 until fireflies) {
                        val phase = i * 0.7f
                        val life = (t + i * 0.05f) % 1f
                        val x = (size.width * 0.1f + size.width * 0.8f * ((0.3f * i + t * 0.15f) % 1f))
                        val y = size.height * (0.8f - 0.6f * life + 0.05f * kotlin.math.sin(t * 3f + phase).toFloat())
                        val glow = 0.3f + 0.7f * kotlin.math.abs(kotlin.math.sin(t * 5f + phase).toFloat())
                        drawCircle(
                            color = Color(0xFF00FFCC).copy(alpha = glow * (1f - life) * pFactor),
                            radius = (4.dp + (i % 3).dp).toPx(),
                            center = Offset(x, y)
                        )
                    }
                }

                "Volcanic Fireball", "Campfire Flame", "Ember Sparks" -> {
                    // Glowing fire plasma balls rising with gradients
                    val fireParticles = if (effectName == "Ember Sparks") 30 else 15
                    for (i in 0 until fireParticles) {
                        val seed = i * 1.5f
                        val life = ((t + i * (1.0f / fireParticles)) % 1f)
                        val x = size.width * (0.05f + 0.9f * (i.toFloat() / fireParticles) + 0.12f * kotlin.math.sin(t * 2 * kotlin.math.PI.toFloat() + seed))
                        val y = size.height * (1.05f - life)
                        
                        val radiusBase = if (effectName == "Ember Sparks") 4.dp else 18.dp
                        val radius = (radiusBase + (i % 4).dp * 4).toPx() * (1.2f - life) * pFactor
                        
                        val colors = if (effectName == "Ember Sparks") {
                            listOf(Color(0xFFFF3D00), Color(0xFFFF9100))
                        } else {
                            listOf(Color(0xFFE65100), Color(0xFFFF3D00), Color(0xFFFFB300))
                        }
                        val color = colors[i % colors.size]
                        
                        if (radius > 0) {
                            drawCircle(
                                color = color.copy(alpha = color.alpha * (1f - life) * pFactor),
                                radius = radius,
                                center = Offset(x, y)
                            )
                        }
                    }
                }

                "Sunset Flare Glow", "Cinematic Blue", "Anamorphic Gold" -> {
                    // Cinematic flare overlays
                    val centerY = size.height * if (effectName.contains("Sunset")) 0.3f else 0.5f
                    val flareColor = when {
                        effectName.contains("Blue") -> Color(0xFF00E5FF)
                        effectName.contains("Gold") -> Color(0xFFFFD700)
                        else -> Color(0xFFFF6D00)
                    }

                    // 1. Center Glow node
                    drawCircle(
                        color = flareColor.copy(alpha = 0.5f * pFactor),
                        radius = size.width * 0.18f,
                        center = Offset(size.width * 0.35f, centerY)
                    )

                    // 2. Anamorphic horizontal path
                    drawLine(
                        color = flareColor.copy(alpha = 0.85f * pFactor),
                        start = Offset(0f, centerY),
                        end = Offset(size.width, centerY),
                        strokeWidth = (2.dp + (rPulse * 3f).dp).toPx()
                    )

                    // 3. Halos/cinematic rings
                    drawCircle(
                        color = flareColor.copy(alpha = 0.15f * pFactor),
                        radius = size.width * 0.38f,
                        center = Offset(size.width * 0.35f, centerY)
                    )

                    // 4. Secondary reflection circles
                    val secondaryPositions = listOf(0.12f, 0.48f, 0.65f, 0.85f)
                    secondaryPositions.forEachIndexed { idx, frac ->
                        val radius = (10.dp + (idx * 4).dp).toPx()
                        drawCircle(
                            color = flareColor.copy(alpha = 0.22f * pFactor),
                            radius = radius,
                            center = Offset(size.width * frac, centerY)
                        )
                    }
                }

                "Pulsing Heart" -> {
                    // High-fidelity vector drawn heart pulsing precisely
                    val scale = 1f + 0.15f * kotlin.math.sin(t * 2 * kotlin.math.PI.toFloat())
                    val sSize = size.width * 0.2f * scale * pFactor
                    val x = size.width / 2
                    val y = size.height * 0.4f
                    if (sSize > 0) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(x, y - sSize * 0.3f)
                            cubicTo(x - sSize * 0.6f, y - sSize * 0.9f, x - sSize, y - sSize * 0.2f, x, y + sSize * 0.8f)
                            cubicTo(x + sSize, y - sSize * 0.2f, x + sSize * 0.6f, y - sSize * 0.9f, x, y - sSize * 0.3f)
                        }
                        drawPath(path, Color(0xFFFF1744).copy(alpha = pFactor))
                    }
                }

                "Fire Flame Group" -> {
                    // Flickering flame emoji shapes drawn with custom gradient fire capsules
                    val pulse = 1f + 0.08f * kotlin.math.sin(t * 3 * kotlin.math.PI.toFloat())
                    val baseWidth = size.width * 0.15f * pulse
                    val x0 = size.width / 2
                    val y0 = size.height * 0.4f
                    
                    // Center high rising flame
                    drawCircle(
                        color = Color(0xFFFF3D00).copy(alpha = 0.85f * pFactor),
                        radius = baseWidth * 0.6f,
                        center = Offset(x0, y0)
                    )
                    drawCircle(
                        color = Color(0xFFFF9100).copy(alpha = 0.9f * pFactor),
                        radius = baseWidth * 0.4f,
                        center = Offset(x0, y0 + (8.dp).toPx())
                    )
                    drawCircle(
                        color = Color(0xFFFFEA00).copy(alpha = 0.95f * pFactor),
                        radius = baseWidth * 0.2f,
                        center = Offset(x0, y0 + (14.dp).toPx())
                    )
                }

                "Thumbs Up Pulse" -> {
                    // Modern round sticker containing animated 👍 symbol
                    val pulse = 1f + 0.12f * kotlin.math.sin(t * 2.5f * kotlin.math.PI.toFloat())
                    val r = (32.dp).toPx() * pulse * pFactor
                    val x = size.width / 2
                    val y = size.height * 0.4f
                    
                    drawCircle(
                        color = Color(0xFF2979FF).copy(alpha = 0.9f * pFactor),
                        radius = r,
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f * pFactor),
                        radius = r + (4.dp).toPx(),
                        center = Offset(x, y)
                    )
                    // Draw Thumb character
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = r * 0.9f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                        // Vertical text alignment
                        val yPos = y - ((paint.descent() + paint.ascent()) / 2f)
                        drawText("👍", x, yPos, paint)
                    }
                }

                "Arrow Point Down" -> {
                    // Glowing bouncing arrow pointing down
                    val bounceY = (15.dp).toPx() * kotlin.math.sin(t * 2 * kotlin.math.PI.toFloat())
                    val x = size.width / 2
                    val y = size.height * 0.3f + bounceY
                    val arrowWidth = (20.dp).toPx() * pFactor
                    
                    val arrowPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x - arrowWidth, y)
                        lineTo(x + arrowWidth, y)
                        lineTo(x + arrowWidth * 0.5f, y + arrowWidth * 0.8f)
                        lineTo(x + arrowWidth * 0.5f, y - arrowWidth)
                        lineTo(x - arrowWidth * 0.5f, y - arrowWidth)
                        lineTo(x - arrowWidth * 0.5f, y + arrowWidth * 0.8f)
                        close()
                    }
                    drawPath(arrowPath, Color(0xFF00E676).copy(alpha = 0.92f * pFactor))
                    // Draw outer aura
                    drawPath(arrowPath, Color(0xFF00E676).copy(alpha = 0.25f * pFactor), style = androidx.compose.ui.graphics.drawscope.Stroke(width = (6.dp).toPx()))
                }

                "Subscribe Overlay" -> {
                    // CapCut-style SUBSCRIBE button overlay
                    val scale = 1f + 0.06f * kotlin.math.sin(t * 2 * kotlin.math.PI.toFloat())
                    val width = (130.dp).toPx() * scale * pFactor
                    val height = (38.dp).toPx() * scale * pFactor
                    val x = size.width / 2
                    val y = size.height * 0.45f
                    
                    val paintBg = android.graphics.Paint().apply {
                        color = android.graphics.Color.RED
                        style = android.graphics.Paint.Style.FILL
                        isAntiAlias = true
                    }
                    val rect = android.graphics.RectF(x - width/2, y - height/2, x + width/2, y + height/2)
                    drawContext.canvas.nativeCanvas.drawRoundRect(rect, (10.dp).toPx(), (10.dp).toPx(), paintBg)
                    
                    // SUBSCRIBE text
                    drawContext.canvas.nativeCanvas.apply {
                        val paintText = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = (13.dp).toPx() * scale
                            isFakeBoldText = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        val yPos = y - ((paintText.descent() + paintText.ascent()) / 2f)
                        drawText("SUBSCRIBE", x, yPos, paintText)
                    }
                }
            }
        }

        return localModifier
    }

    // Retranslates selected custom fields of high-fidelity filters to ColorFilter live inside Coil AsyncImage
    fun getColorFilterForCategory(category: String, filterName: String, intensity: Int): ColorFilter? {
        if (filterName.equals("Normal", ignoreCase = true) || category.equals("Normal", ignoreCase = true)) {
            return null
        }
        val filter = FilterManager.ALL_FILTERS.firstOrNull {
            it.name.equals(filterName, ignoreCase = true)
        } ?: return null
        
        val matrix = filter.colorFilterMatrix ?: return null
        return ColorFilter.colorMatrix(matrix)
    }
}
