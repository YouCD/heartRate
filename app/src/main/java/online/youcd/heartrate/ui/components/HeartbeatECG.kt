package online.youcd.heartrate.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private const val LOOP_DURATION_NS = 2_000_000_000L
private const val MAX_BEAMS = 2

private class Beam {
    var progress by mutableStateOf(0f)
}

/**
 * 心电图背景（电流效果）：
 * 仅当 bpm 数值变化时（tick 递增）发送一个光点，光点沿波形
 * 缓慢扫描到尾部后停在原地；新旧光点互不打断，数据更新快时
 * 上一个光点也不会消失。
 */
@Composable
fun HeartbeatECGBackground(
    bpm: Int,
    tick: Long,
    modifier: Modifier = Modifier,
    color: Color,
) {
    val beams = remember { mutableStateListOf<Beam>() }
    val scope = rememberCoroutineScope()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val currentTick by rememberUpdatedState(tick)

    LaunchedEffect(Unit) {
        snapshotFlow { currentTick }
            .filter { it > 0 }
            .collect {
                scope.launch {
                    if (beams.size >= MAX_BEAMS) beams.removeAt(0)
                    val beam = Beam()
                    beams.add(beam)
                    val start = withFrameNanos { it }
                    while (true) {
                        val now = withFrameNanos { it }
                        val t = ((now - start) / LOOP_DURATION_NS.toDouble()).toFloat()
                            .coerceIn(0f, 1f)
                        beam.progress = t
                        if (t >= 1f) break
                    }
                    beams.remove(beam)
                }
            }
    }

    Canvas(modifier = modifier) {
        val baseline = size.height * 0.52f
        val amp = size.height * 0.18f
        val wavePath = buildMultiECGPath(size.width, baseline, amp)

        // 底层暗波形
        drawPath(
            path = wavePath,
            color = color.copy(alpha = 0.10f),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )

        if (bpm > 0 && beams.isNotEmpty()) {
            val pathMeasure = PathMeasure()
            pathMeasure.setPath(wavePath, false)
            val totalLength = pathMeasure.length
            if (totalLength > 0f) {
                // 每个光束画各自的亮线：旧的稍暗、新的更亮
                beams.forEachIndexed { index, beam ->
                    val isLatest = index == beams.lastIndex
                    val litPath = Path()
                    pathMeasure.getSegment(0f, totalLength * beam.progress, litPath, true)
                    drawPath(
                        path = litPath,
                        color = color.copy(alpha = if (isLatest) 0.9f else 0.5f),
                        style = Stroke(
                            width = if (isLatest) 2.6.dp.toPx() else 2.0.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    )
                }

                // 每个光点
                beams.forEach { beam ->
                    val point = pathMeasure.getPosition(totalLength * beam.progress)
                    drawCircle(
                        color = color.copy(alpha = 0.45f),
                        radius = 7.dp.toPx(),
                        center = point
                    )
                    drawCircle(
                        color = onSurface,
                        radius = 3.dp.toPx(),
                        center = point
                    )
                }
            }
        }
    }
}

private fun buildMultiECGPath(width: Float, baseline: Float, amp: Float): Path {
    val cellCount = 1
    val cellW = width / cellCount
    return Path().apply {
        repeat(cellCount) { i ->
            val x0 = i * cellW
            // 平线
            moveTo(x0, baseline)
            lineTo(x0 + cellW * 0.06f, baseline)
            // 附加弯折1（P 波前的小负波）
            cubicTo(
                x0 + cellW * 0.08f, baseline + 0.08f * amp,
                x0 + cellW * 0.10f, baseline + 0.08f * amp,
                x0 + cellW * 0.12f, baseline
            )
            lineTo(x0 + cellW * 0.16f, baseline)
            // P 波
            cubicTo(
                x0 + cellW * 0.20f, baseline - 0.12f * amp,
                x0 + cellW * 0.24f, baseline - 0.12f * amp,
                x0 + cellW * 0.28f, baseline
            )
            lineTo(x0 + cellW * 0.34f, baseline)
            // Q 波
            lineTo(x0 + cellW * 0.37f, baseline + 0.12f * amp)
            // R 波（大幅上冲）
            lineTo(x0 + cellW * 0.42f, baseline - amp)
            // S 波
            lineTo(x0 + cellW * 0.47f, baseline + 0.32f * amp)
            lineTo(x0 + cellW * 0.50f, baseline)
            // 附加弯折2（QRS 后的小正波）
            cubicTo(
                x0 + cellW * 0.56f, baseline - 0.10f * amp,
                x0 + cellW * 0.62f, baseline - 0.10f * amp,
                x0 + cellW * 0.66f, baseline
            )
            lineTo(x0 + cellW * 0.70f, baseline)
            // T 波
            cubicTo(
                x0 + cellW * 0.76f, baseline - 0.24f * amp,
                x0 + cellW * 0.84f, baseline - 0.24f * amp,
                x0 + cellW * 0.88f, baseline
            )
            // U 波（T 波后的小弯折）
            cubicTo(
                x0 + cellW * 0.92f, baseline - 0.10f * amp,
                x0 + cellW * 0.96f, baseline - 0.10f * amp,
                x0 + cellW * 0.98f, baseline
            )
            lineTo(x0 + cellW, baseline)
        }
    }
}
