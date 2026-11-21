package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

enum class CoreVisualState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun HolographicCoreVisualizer(
    state: CoreVisualState,
    rmsLevel: Float,
    statusLabel: String,
    onCoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val surfaceColor = MaterialTheme.colorScheme.surface

    val transition = rememberInfiniteTransition(label = "jarvis_core_anim")
    val orbitAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    CoreVisualState.THINKING -> 2200
                    CoreVisualState.LISTENING -> 3400
                    CoreVisualState.SPEAKING -> 4200
                    CoreVisualState.IDLE -> 9000
                },
                easing = LinearEasing
            )
        ),
        label = "orbit"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing)
        ),
        label = "wave"
    )

    val activeAccent: Color = when (state) {
        CoreVisualState.IDLE -> primary
        CoreVisualState.LISTENING -> secondary
        CoreVisualState.THINKING -> tertiary
        CoreVisualState.SPEAKING -> primary
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(188.dp)
                .testTag("holographic_core_button")
                .semantics { contentDescription = "Jarvis holographic AI core. Tap to toggle voice." }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 94.dp),
                    onClick = onCoreClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.minDimension * 0.42f
                val boost = if (state == CoreVisualState.LISTENING) (rmsLevel * 0.14f) else 0f
                val coreRadius = baseRadius * 0.48f * (pulse + boost)

                // Ambient glow halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activeAccent.copy(alpha = 0.32f),
                            activeAccent.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = baseRadius * 1.15f
                    ),
                    radius = baseRadius * 1.15f,
                    center = center
                )

                // Outer segmented orbital ring
                drawArc(
                    color = primary.copy(alpha = 0.72f),
                    startAngle = orbitAngle,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                    size = Size(baseRadius * 2f, baseRadius * 2f),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = secondary.copy(alpha = 0.85f),
                    startAngle = orbitAngle + 180f,
                    sweepAngle = 95f,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                    size = Size(baseRadius * 2f, baseRadius * 2f),
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Counter-rotating inner tactical ring
                val midRadius = baseRadius * 0.76f
                drawArc(
                    color = activeAccent.copy(alpha = 0.65f),
                    startAngle = -orbitAngle * 1.3f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(center.x - midRadius, center.y - midRadius),
                    size = Size(midRadius * 2f, midRadius * 2f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = tertiary.copy(alpha = 0.55f),
                    startAngle = -orbitAngle * 1.3f + 190f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - midRadius, center.y - midRadius),
                    size = Size(midRadius * 2f, midRadius * 2f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Inner core sphere
                drawCircle(
                    color = surfaceColor,
                    radius = coreRadius,
                    center = center
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activeAccent.copy(alpha = 0.9f),
                            primary.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = coreRadius
                    ),
                    radius = coreRadius,
                    center = center
                )
                drawCircle(
                    color = activeAccent,
                    radius = coreRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "JARVIS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (state) {
                        CoreVisualState.IDLE -> "TAP MIC"
                        CoreVisualState.LISTENING -> "LISTENING"
                        CoreVisualState.THINKING -> "THINKING"
                        CoreVisualState.SPEAKING -> "SPEAKING"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = activeAccent
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Live Waveform Visualizer
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .height(34.dp)
        ) {
            val barCount = 28
            val spacing = size.width / barCount
            val barWidth = (spacing * 0.54f).coerceAtLeast(3.dp.toPx())
            val centerY = size.height / 2f
            val energy = when (state) {
                CoreVisualState.IDLE -> 0.18f
                CoreVisualState.LISTENING -> (0.35f + rmsLevel * 0.65f).coerceIn(0.2f, 1f)
                CoreVisualState.THINKING -> 0.58f
                CoreVisualState.SPEAKING -> 0.82f
            }

            for (i in 0 until barCount) {
                val normPos = i.toFloat() / (barCount - 1)
                val envelope = sin(normPos * PI).toFloat()
                val wave = (sin(wavePhase + i * 0.55f) * 0.5f + 0.5f)
                val barHeight = (size.height * (0.16f + 0.84f * energy * envelope * wave))
                    .coerceAtLeast(4.dp.toPx())
                val x = i * spacing + (spacing - barWidth) / 2f
                val color = if (i % 3 == 0) secondary else primary
                drawRoundRect(
                    color = color.copy(alpha = 0.35f + 0.65f * energy),
                    topLeft = Offset(x, centerY - barHeight / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
