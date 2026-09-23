package com.ritvyom.yashoraReelgenerator.presentation.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ritvyom.yashoraReelgenerator.presentation.components.FilmReelLogo
import kotlinx.coroutines.delay

@Composable
fun SplashPage(onSplashFinished: () -> Unit) {
    val scaleAnim = rememberInfiniteTransition(label = "ScaleLogo")
    val scale by scaleAnim.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoScale"
    )

    LaunchedEffect(Unit) {
        delay(2500)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E041A), // Dark premium midnight violet matches our new launcher background
                        Color(0xFF05010B), // Premium dark core black
                        Color(0xFF010003)  // Bottom pure obsidian
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Beautiful high fidelity drawn Film Reel Logo matching user identity
            FilmReelLogo(
                modifier = Modifier
                    .size(140.dp)
                    .scale(scale)
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = "YASHORA",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                fontFamily = FontFamily.SansSerif
            )
            
            Text(
                text = "REEL GENERATOR",
                color = Color(0xFF00F0FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Powering Cinematic AI Content",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 18.dp)
            )
        }
    }
}
