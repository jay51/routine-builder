package dev.routinebuilder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

@Composable
internal fun rememberMountainJourney(tasks: List<TodayRoutineTask>): Animatable<Float, AnimationVector1D> {
    val completed = tasks.count { it.log != null }
    val allDone = tasks.isNotEmpty() && completed == tasks.size
    val destination = completed.toFloat() + if (allDone) 1f else 0f
    val identity = tasks.map { it.task.id }.toSet() to java.time.LocalDate.now()
    val journey = remember { Animatable(destination) }
    var previousIdentity by remember { mutableStateOf(identity) }
    LaunchedEffect(completed, identity) {
        val reset = identity != previousIdentity || destination < journey.value
        previousIdentity = identity
        if (reset) journey.snapTo(destination)
        else if (destination > journey.value) {
            // The extra journey segment celebrates only after the walker has crossed the flag.
            if (journey.value < completed) {
                journey.animateTo(completed.toFloat(), tween(if (allDone) 3_000 else 2_200, easing = LinearEasing))
            }
            if (allDone) journey.animateTo(destination, tween(1_600, easing = LinearEasing))
        }
    }
    return journey
}

@Composable
internal fun FinishConfetti(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (progress <= 0f || progress >= 1f) return@Canvas
        val colors = listOf(Color(0xFFF2C56B), Color(0xFF6FA884), Color(0xFFE79779), Color(0xFF8CAAC7))
        val origin = Offset(size.width * .62f, size.height * .75f)
        repeat(32) { index ->
            val delay = (index % 4) * .025f
            val time = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (time <= 0f) return@repeat
            val horizontal = ((index * 17 % 31) / 30f - .5f) * size.width * 1.5f
            val lift = size.height * (1.1f + (index * 7 % 11) / 10f)
            val point = origin + Offset(horizontal * time, -lift * time + size.height * 2f * time * time)
            val alpha = (1f - ((time - .55f) / .45f).coerceIn(0f, 1f))
            rotate(index * 29f + time * 300f, point) {
                drawRect(colors[index % colors.size].copy(alpha = alpha), point,
                    androidx.compose.ui.geometry.Size(4.dp.toPx(), 7.dp.toPx()))
            }
        }
    }
}

@Composable
internal fun MountainMilestone(completed: Int, total: Int, journey: Animatable<Float, AnimationVector1D>) {
    val progress = if (total == 0) 0f else completed.toFloat() / total
    val attention = remember { Animatable(0f) }
    val lifecycleOwner = LocalView.current.findViewTreeLifecycleOwner()
    val waiting = total > 0 && completed < total && !journey.isRunning
    LaunchedEffect(waiting, lifecycleOwner) {
        attention.snapTo(0f)
        if (!waiting) return@LaunchedEffect
        lifecycleOwner?.lifecycle?.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            attention.snapTo(0f)
            delay(5_000)
            while (currentCoroutineContext().isActive) {
                // Disabled system animations should also disable the attention gesture.
                val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
                if (scale > 0f) {
                    attention.animateTo(1f, tween(1_100, easing = LinearEasing))
                    attention.snapTo(0f)
                }
                delay(if (scale > 0f) (5_000L - (1_100 * scale).toLong()).coerceAtLeast(0L) else 5_000L)
            }
        }
    }
    // Animatable respects Android's animation duration scale, including disabled animations.
    val forest = Color(0xFF395746)
    val gold = Color(0xFFF2C56B)

    Canvas(Modifier.fillMaxWidth().height(56.dp).clipToBounds().semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
        contentDescription = if (total == 0) "No tasks planned."
            else "$completed of $total tasks completed. $completed mountains crossed.${if (completed == total) " All tasks done." else " Next mountain ahead."}"
    }) {
        val traveled = journey.value.coerceIn(0f, total.toFloat())
        val crossed = floor(traveled).toInt()
        val phase = traveled - crossed
        val shift = FastOutSlowInEasing.transform(((phase - .25f) / .75f).coerceIn(0f, 1f))
        // End the scene at the ground line rather than leaving an empty strip below it.
        // Reserve only the stroke/foot clearance needed to avoid clipping the walker.
        val ground = size.height - 2.dp.toPx()

        // Draw only nearby mountains, regardless of the routine's total task count.
        fun mountainPosition(relative: Int): Triple<Float, Float, Float> = when (relative) {
            -3 -> Triple(-.50f, .12f, .06f)
            -2 -> Triple(-.20f, .20f, .12f)
            -1 -> Triple(.10f, .28f, .20f)
            0 -> Triple(.46f, .50f, .40f)
            1 -> Triple(.92f, .66f, .62f)
            else -> Triple(1.45f, .80f, .75f)
        }
        fun mix(start: Float, end: Float) = start + (end - start) * shift
        fun drawMountain(center: Float, width: Float, height: Float, color: Color) {
            val x = center * size.width
            val halfWidth = width * size.width / 2f
            val peak = ground - height * size.height
            val silhouette = Path().apply {
                moveTo(x - halfWidth, ground)
                lineTo(x, peak)
                lineTo(x + halfWidth, ground)
                close()
            }
            drawPath(silhouette, color)
            val face = Path().apply {
                moveTo(x, peak)
                lineTo(x - halfWidth * .12f, ground)
                lineTo(x + halfWidth, ground)
                close()
            }
            drawPath(face, forest.copy(alpha = .10f))
        }
        if (total == 0) drawMountain(.62f, .65f, .38f, Color(0xFFF0F3EF))
        for (index in (crossed + 2) downTo (crossed - 2)) {
            if (index !in 0 until total) continue
            val relative = index - crossed
            val start = mountainPosition(relative)
            val end = mountainPosition(relative - 1)
            fun mountainColor(position: Int) = when {
                position < 0 -> Color(0xFFD3E1D5)
                position == 0 -> Color(0xFFE5EEE6)
                else -> Color(0xFFF0F3EF)
            }
            val color = lerp(mountainColor(relative), mountainColor(relative - 1), shift)
            drawMountain(mix(start.first, end.first), mix(start.second, end.second), mix(start.third, end.third), color)
        }
        drawLine(forest.copy(alpha = .18f), Offset(0f, ground), Offset(size.width, ground), 1.5.dp.toPx(), StrokeCap.Round)

        // The finish is part of the landscape: it comes into view beyond the last mountain
        // and moves with the terrain, rather than appearing when the count reaches the total.
        if (total > 0) {
            fun finishPosition(mountainsAhead: Int): Float = when (mountainsAhead) {
                0 -> .62f
                1 -> .84f
                else -> .84f + .56f * (mountainsAhead - 1)
            }
            val mountainsAhead = total - crossed
            val finishX = if (mountainsAhead == 0) .62f
                else mix(finishPosition(mountainsAhead), finishPosition(mountainsAhead - 1))
            val pole = Offset(size.width * finishX, ground)
            drawLine(forest, pole, pole - Offset(0f, 28.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            val flag = Path().apply {
                moveTo(pole.x, pole.y - 28.dp.toPx())
                lineTo(pole.x + 16.dp.toPx(), pole.y - 22.dp.toPx())
                lineTo(pole.x, pole.y - 16.dp.toPx())
                close()
            }
            drawPath(flag, gold)
        }

        // Keep the walker on the mountain's changing surface while it shrinks and moves behind him.
        val lastMountain = total > 0 && crossed == total - 1
        val finished = total > 0 && crossed == total
        val mountainWalkEnd = if (lastMountain) .65f else .80f
        val walking = (phase / mountainWalkEnd).coerceAtMost(1f)
        val nextStart = if (lastMountain || finished) .82f else .21f
        val mountainCenter = mix(.46f, .10f)
        val mountainWidth = mix(.50f, .28f)
        val mountainHeight = mix(.40f, .20f)
        val surfaceX = mountainCenter - mountainWidth / 2f + mountainWidth * walking
        val walkOff = ((phase - mountainWalkEnd) / (1f - mountainWalkEnd)).coerceIn(0f, 1f)
        val x = if (finished) .82f else surfaceX + (nextStart - surfaceX) * walkOff
        val elevation = mountainHeight * (1f - abs(2f * walking - 1f))
        val gesture = if (waiting) attention.value else 0f
        val jump = sin(((gesture - .1f) / .5f).coerceIn(0f, 1f) * Math.PI.toFloat()).coerceAtLeast(0f) * 8f
        val wave = sin(gesture * Math.PI.toFloat()).coerceAtLeast(0f)
        val foot = Offset(size.width * x, ground - size.height * elevation - jump.dp.toPx())
        val gait = sin(phase * Math.PI.toFloat() * 12f)
        // A small illustrated hiker, facing the next mountain. Keep the whole figure
        // within the compact scene even when he reaches the summit.
        val skin = Color(0xFFDCA77B)
        val trousers = Color(0xFF34465B)
        val shoes = Color(0xFF26352C)
        val bob = abs(gait) * .6f
        fun point(dx: Float, dy: Float) = foot + Offset(dx.dp.toPx(), (dy - bob).dp.toPx())
        fun limb(color: Color, start: Offset, joint: Offset, end: Offset, width: Float) {
            drawLine(color, start, joint, width.dp.toPx(), StrokeCap.Round)
            drawLine(color, joint, end, width.dp.toPx(), StrokeCap.Round)
        }
        val stride = gait * 3f
        val backLift = gait.coerceAtLeast(0f) * 1.5f
        val frontLift = -gait.coerceAtMost(0f) * 1.5f
        val backFoot = foot + Offset((-2f - stride).dp.toPx(), (-1f - backLift).dp.toPx())
        val frontFoot = foot + Offset((2f + stride).dp.toPx(), (-1f - frontLift).dp.toPx())
        // Knees follow their feet with a modest forward bend. Separate hip anchors
        // and shorter strides avoid the exaggerated scissor shape of the old gait.
        limb(trousers.copy(alpha = .65f), point(-1f, -9f), point(-.5f - stride * .45f, -5f - backLift * .5f), backFoot, 2.5f)
        drawLine(shoes, backFoot - Offset(1.dp.toPx(), 0f), backFoot + Offset(2.dp.toPx(), 0f), 2.dp.toPx(), StrokeCap.Round)
        limb(skin.copy(alpha = .8f), point(0f, -17f), point(-2f + gait * 3f, -13f), point(-1f + gait * 4f, -10f), 2f)

        // Rounded backpack, fitted shirt, and a visible shoulder strap.
        drawRoundRect(gold, point(-6f, -18f), Size(5.dp.toPx(), 9.dp.toPx()), CornerRadius(2.dp.toPx()))
        drawLine(Color(0xFFCC9A45), point(-5f, -16f), point(-5f, -12f), 1.dp.toPx(), StrokeCap.Round)
        drawLine(skin, point(.5f, -21f), point(.5f, -18f), 2.5.dp.toPx(), StrokeCap.Round)
        val shirt = Path().apply {
            moveTo(point(-2f, -19f).x, point(-2f, -19f).y)
            quadraticBezierTo(point(1f, -20f).x, point(1f, -20f).y, point(3f, -17f).x, point(3f, -17f).y)
            lineTo(point(2f, -9f).x, point(2f, -9f).y)
            lineTo(point(-3f, -9f).x, point(-3f, -9f).y)
            close()
        }
        drawPath(shirt, forest)
        drawLine(gold, point(-1.5f, -18f), point(-2f, -12f), 1.dp.toPx(), StrokeCap.Round)
        limb(trousers, point(1f, -9f), point(2f + stride * .45f, -5f - frontLift * .5f), frontFoot, 2.5f)
        drawLine(shoes, frontFoot - Offset(1.dp.toPx(), 0f), frontFoot + Offset(2.5.dp.toPx(), 0f), 2.dp.toPx(), StrokeCap.Round)
        val elbow = point(4f - gait * 2.5f + wave * 2f, -14f - wave * 6f)
        val hand = point(5f - gait * 3.5f + wave * (2f + sin(gesture * Math.PI.toFloat() * 6f) * 2f), -12f - wave * 15f)
        limb(skin, point(2f, -17f), elbow, hand, 2f)
        drawCircle(skin, 1.3.dp.toPx(), hand)
        drawLine(forest, point(2f, -17f), point(3f - gait + wave, -15.5f - wave * 2f), 3.dp.toPx(), StrokeCap.Round)

        val head = point(1f, -23f)
        drawCircle(skin, 4.dp.toPx(), head)
        drawCircle(skin, 1.2.dp.toPx(), point(5f, -22.5f))
        drawRoundRect(shoes, point(-3f, -27f), Size(7.dp.toPx(), 3.dp.toPx()), CornerRadius(1.5.dp.toPx()))
        drawLine(shoes, point(-2.5f, -25f), point(-2.5f, -22.5f), 1.5.dp.toPx(), StrokeCap.Round)
        drawCircle(shoes, .65.dp.toPx(), point(3f, -23.5f))

    }
}
