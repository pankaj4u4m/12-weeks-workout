package com.personal.twelveweek.web

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ports of the Android app's ui/TrainingComponents.kt into commonMain's
 * `web` package (see WebTheme.kt for why this is a standalone copy rather
 * than a shared import) — same shapes, same progress/route visuals.
 */

@Composable
fun TrainingCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactive = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    Surface(
        modifier = interactive,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun ProgressBand(
    fraction: Float,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    markerColor: Color = MaterialTheme.colorScheme.secondary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant
) {
    val safe = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = safe, label = "progress band")
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(animated, 0f..1f) }
    ) {
        val radius = size.height / 2f
        drawRoundRect(color = trackColor, size = size, cornerRadius = CornerRadius(radius, radius))
        if (animated > 0f) {
            val width = size.width * animated
            drawRoundRect(color = activeColor, size = Size(width, size.height), cornerRadius = CornerRadius(radius, radius))
            drawCircle(
                color = markerColor,
                radius = radius,
                center = Offset(width.coerceIn(radius, size.width - radius), radius)
            )
        }
    }
}

@Composable
fun WeekBand(
    weekFractions: List<Float>,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val current = MaterialTheme.colorScheme.secondary
    val future = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val track = MaterialTheme.colorScheme.outlineVariant
    val description = if (currentIndex in weekFractions.indices) {
        "Week ${currentIndex + 1} of ${weekFractions.size}"
    } else {
        "Program route complete"
    }

    Column(modifier = modifier.semantics { contentDescription = description }) {
        Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            if (weekFractions.isEmpty()) return@Canvas
            val start = 10.dp.toPx()
            val end = size.width - start
            val gap = if (weekFractions.size == 1) 0f else (end - start) / (weekFractions.size - 1)
            val y = size.height / 2f

            drawLine(track, Offset(start, y), Offset(end, y), 6.dp.toPx(), StrokeCap.Round)

            weekFractions.forEachIndexed { index, fraction ->
                if (index < weekFractions.lastIndex && fraction >= 1f) {
                    drawLine(
                        primary,
                        Offset(start + gap * index, y),
                        Offset(start + gap * (index + 1), y),
                        6.dp.toPx(),
                        StrokeCap.Round
                    )
                }
            }

            weekFractions.forEachIndexed { index, fraction ->
                val x = start + gap * index
                val color = when {
                    index == currentIndex -> current
                    fraction >= 1f -> primary
                    else -> future
                }
                drawCircle(color, if (index == currentIndex) 8.dp.toPx() else 6.dp.toPx(), Offset(x, y))
                if (fraction < 1f && index != currentIndex) {
                    drawCircle(
                        color = outline,
                        radius = 6.dp.toPx(),
                        center = Offset(x, y),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("W1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                "W${weekFractions.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Three crossing resistance-band strokes — used at completion / empty
 *  states, same as the Android app's `ui.ResistanceBandMark`. */
@Composable
fun ResistanceBandMark(modifier: Modifier = Modifier, muted: Boolean = false) {
    val alpha = if (muted) 0.35f else 1f
    Canvas(modifier = modifier) {
        val top = Path().apply {
            moveTo(0f, size.height * 0.34f)
            cubicTo(size.width * 0.24f, size.height * 0.02f, size.width * 0.62f, size.height * 0.66f, size.width, size.height * 0.24f)
        }
        val middle = Path().apply {
            moveTo(0f, size.height * 0.58f)
            cubicTo(size.width * 0.30f, size.height * 0.92f, size.width * 0.68f, size.height * 0.10f, size.width, size.height * 0.62f)
        }
        val lower = Path().apply {
            moveTo(0f, size.height * 0.78f)
            cubicTo(size.width * 0.35f, size.height * 0.48f, size.width * 0.70f, size.height, size.width, size.height * 0.70f)
        }
        drawPath(top, WebBandBlue.copy(alpha = alpha), style = Stroke(14.dp.toPx(), cap = StrokeCap.Round))
        drawPath(middle, WebBandCoral.copy(alpha = alpha), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
        drawPath(lower, WebBandMint.copy(alpha = alpha), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Web port of the Android app's `ConfettiBurst` — same shape, same
 *  ~1.2s one-shot burst, using the Web Band palette. */
@Composable
fun WebConfettiBurst(modifier: Modifier = Modifier, particleCount: Int = 28) {
    val burstColors = listOf(
        WebBandBlue, WebBandCoral, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary
    )
    val particles = remember {
        List(particleCount) { i ->
            WebConfettiParticle(
                angle = Random.nextFloat() * (kotlin.math.PI.toFloat() * 2f),
                speed = 0.25f + Random.nextFloat() * 0.35f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 540f,
                size = (6 + Random.nextInt(6)).dp,
                color = burstColors[i % burstColors.size]
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 1200, easing = LinearEasing))
    }
    if (progress.value >= 1f) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val t = progress.value
        val fade = (1f - t).coerceIn(0f, 1f)
        val reach = minOf(size.width, size.height)
        val originX = size.width / 2f
        val originY = size.height * 0.18f
        particles.forEach { p ->
            val outward = reach * p.speed * t
            val x = originX + cos(p.angle) * outward
            val fall = size.height * 0.55f * t * t
            val y = originY + sin(p.angle) * outward * 0.4f + fall
            val half = p.size.toPx() / 2f
            rotate(p.rotationSpeed * t, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = fade),
                    topLeft = Offset(x - half, y - half * 0.6f),
                    size = Size(p.size.toPx(), p.size.toPx() * 0.6f)
                )
            }
        }
    }
}

private data class WebConfettiParticle(
    val angle: Float,
    val speed: Float,
    val rotationSpeed: Float,
    val size: Dp,
    val color: Color
)
