package online.youcd.heartrate.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.youcd.heartrate.data.model.HeartRateZone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class XAxisMode {
    TRAINING_TIME,
    CLOCK_TIME
}

/**
 * 手绘心率变化趋势折线图：
 * 区间背景色带、Y/X 轴刻度与标题、贝塞尔平滑折线、渐变填充、
 * 最高/最低点标注、长按十字准星；支持训练时间/自然时间 X 轴切换。
 */
@Composable
fun HeartRateChart(
    samples: List<Int>,
    maxHr: Int,
    modifier: Modifier = Modifier,
    lineColor: Color,
    intervalMs: Long = 1000,
    timestamps: List<Long>? = null,
    xAxisMode: XAxisMode = XAxisMode.TRAINING_TIME,
    yAxisLabel: String = "BPM"
) {
    val textMeasurer = rememberTextMeasurer()
    var crosshairIndex by remember { mutableStateOf<Int?>(null) }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val outline = MaterialTheme.colorScheme.outline

    val labelStyle = TextStyle(
        color = onSurface.copy(alpha = 0.7f),
        fontSize = 10.sp
    )
    val axisTitleStyle = TextStyle(
        color = onSurface.copy(alpha = 0.85f),
        fontSize = 11.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
    )

    key(xAxisMode) {
        Canvas(
            modifier = modifier.pointerInput(samples.size) {
                if (samples.size < 2) return@pointerInput
                val leftPadding = 36.dp.toPx()
                val total = (size.width - 52.dp.toPx()).coerceAtLeast(1f)
                fun toIndex(x: Float): Int =
                    (((x - leftPadding) / total).coerceIn(0f, 1f) * (samples.size - 1)).roundToInt()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    crosshairIndex = toIndex(down.position.x)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            crosshairIndex = toIndex(event.changes.first().position.x)
                        }
                    } catch (_: Exception) {
                    } finally {
                        crosshairIndex = null
                    }
                }
            }
        ) {
            if (samples.isEmpty()) return@Canvas

            val leftPadding = 36.dp.toPx()
            val rightPadding = 16.dp.toPx()
            val topPadding = 28.dp.toPx()
            val bottomPadding = 36.dp.toPx()
            val chartWidth = size.width - leftPadding - rightPadding
            val chartHeight = size.height - topPadding - bottomPadding

            val maxValue = maxHr.coerceAtLeast(samples.maxOrNull() ?: 0) + 15
            val minValue = 40f
            val yRange = (maxValue - minValue).coerceAtLeast(1f)

            fun xFor(index: Int): Float =
                leftPadding + chartWidth * index / (samples.size - 1).coerceAtLeast(1)

            fun yFor(value: Float): Float =
                topPadding + chartHeight * (maxValue - value) / yRange

            // 背景区间色带
            HeartRateZone.ZONES.forEach { zone ->
                val topY = yFor(maxHr * zone.maxPercent)
                val bottomY = yFor(maxHr * zone.minPercent)
                drawRect(
                    color = Color(zone.color).copy(alpha = 0.08f),
                    topLeft = Offset(leftPadding, topY),
                    size = androidx.compose.ui.geometry.Size(chartWidth, (bottomY - topY))
                )
            }

            // Y 轴刻度
            val gridColor = outline.copy(alpha = 0.15f)
            val ySteps = 5
            for (i in 0..ySteps) {
                val value = minValue + yRange * i / ySteps
                val y = yFor(value)
                drawLine(
                    color = gridColor,
                    start = Offset(leftPadding, y),
                    end = Offset(size.width - rightPadding, y),
                    strokeWidth = 1.dp.toPx()
                )
                val label = textMeasurer.measure(value.roundToInt().toString(), labelStyle)
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(
                        (leftPadding - label.size.width - 4.dp.toPx()),
                        y - label.size.height / 2
                    )
                )
            }

            // Y 轴标题
            val yTitle = textMeasurer.measure(yAxisLabel, axisTitleStyle)
            drawText(
                textLayoutResult = yTitle,
                topLeft = Offset(
                    (leftPadding - yTitle.size.width) / 2,
                    topPadding - yTitle.size.height - 2.dp.toPx()
                )
            )

            // X 轴刻度
            val baseTime = timestamps?.firstOrNull()
            val xTicks = minOf(5, samples.size)
            for (i in 0 until xTicks) {
                val index = (samples.size - 1) * i / (xTicks - 1)
                val timeText = when (xAxisMode) {
                    XAxisMode.TRAINING_TIME -> {
                        val seconds = if (baseTime != null && timestamps != null) {
                            (timestamps[index] - baseTime) / 1000
                        } else {
                            index * (intervalMs / 1000)
                        }
                        formatSeconds(seconds)
                    }
                    XAxisMode.CLOCK_TIME ->
                        timestamps?.getOrNull(index)?.let { formatClockTime(it) } ?: ""
                }
                if (timeText.isEmpty()) continue
                val label = textMeasurer.measure(timeText, labelStyle)
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(
                        xFor(index) - label.size.width / 2,
                        size.height - bottomPadding + 4.dp.toPx()
                    )
                )
            }

            // X 轴标题
            val xTitleText = if (xAxisMode == XAxisMode.TRAINING_TIME) "训练时间" else "自然时间"
            val xTitle = textMeasurer.measure(xTitleText, axisTitleStyle)
            drawText(
                textLayoutResult = xTitle,
                topLeft = Offset(
                    (size.width - xTitle.size.width) / 2,
                    size.height - xTitle.size.height
                )
            )

            if (samples.size < 2) return@Canvas

            val points = samples.mapIndexed { index, bpm ->
                Offset(xFor(index), yFor(bpm.toFloat()))
            }

            // 渐变填充
            val fillPath = Path().apply {
                moveTo(points.first().x, size.height - bottomPadding)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, size.height - bottomPadding)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.22f), Color.Transparent),
                    startY = topPadding,
                    endY = size.height - bottomPadding
                )
            )

            // 发光底 + 主折线
            val linePath = Path().apply { smoothLine(points) }
            drawPath(
                path = linePath,
                color = lineColor.copy(alpha = 0.35f),
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
            )

            // 最高 / 最低点标注
            val maxIndex = samples.indexOf(samples.maxOrNull() ?: 0)
            val minIndex = samples.indexOf(samples.minOrNull() ?: 0)
            val peakPairs = listOf(maxIndex to "↑", minIndex to "↓").distinctBy { it.first }
            peakPairs.forEach { (index, arrow) ->
                val point = points[index]
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = lineColor,
                    radius = 2.4.dp.toPx(),
                    center = point
                )
                val peakText = "$arrow ${samples[index]}"
                val label = textMeasurer.measure(
                    peakText,
                    TextStyle(color = Color.White, fontSize = 9.sp)
                )
                val bubbleY = (point.y - label.size.height - 6.dp.toPx())
                    .coerceAtLeast(topPadding - 4.dp.toPx())
                val bubbleX = (point.x - label.size.width / 2)
                    .coerceIn(leftPadding, size.width - rightPadding - label.size.width)
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(bubbleX, bubbleY)
                )
            }

            // 十字准星
            crosshairIndex?.let { index ->
                if (index in samples.indices) {
                    val x = xFor(index)
                    val y = yFor(samples[index].toFloat())
                    drawLine(
                        color = onSurface.copy(alpha = 0.3f),
                        start = Offset(x, topPadding),
                        end = Offset(x, size.height - bottomPadding),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = onSurface.copy(alpha = 0.3f),
                        start = Offset(leftPadding, y),
                        end = Offset(size.width - rightPadding, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawCircle(
                        color = lineColor,
                        radius = 5.dp.toPx(),
                        center = Offset(x, y)
                    )
                    val timeText = when (xAxisMode) {
                        XAxisMode.TRAINING_TIME -> formatSeconds(index * (intervalMs / 1000))
                        XAxisMode.CLOCK_TIME ->
                            timestamps?.getOrNull(index)?.let { formatClockTime(it) } ?: ""
                    }
                    val info = "${samples[index]} bpm · $timeText"
                    val label = textMeasurer.measure(
                        info,
                        TextStyle(color = onSurface, fontSize = 9.sp)
                    )
                    val infoX = (x - label.size.width / 2)
                        .coerceIn(leftPadding, size.width - rightPadding - label.size.width)
                    val infoY = (y - label.size.height - 10.dp.toPx())
                        .coerceAtLeast(topPadding)
                    drawText(
                        textLayoutResult = label,
                        topLeft = Offset(infoX, infoY)
                    )
                }
            }
        }
    }
}

private fun Path.smoothLine(points: List<Offset>) {
    if (points.isEmpty()) return
    moveTo(points.first().x, points.first().y)
    if (points.size == 2) {
        lineTo(points[1].x, points[1].y)
        return
    }
    for (i in 1 until points.size - 1) {
        val p0 = points[i - 1]
        val p1 = points[i]
        val p2 = points[i + 1]
        val cp1 = Offset(p1.x - (p2.x - p0.x) / 6f, p1.y - (p2.y - p0.y) / 6f)
        val cp2 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
        cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p1.x, p1.y)
    }
    lineTo(points.last().x, points.last().y)
}

private fun formatSeconds(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

private fun formatClockTime(millis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))
