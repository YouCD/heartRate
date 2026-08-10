package online.youcd.heartrate.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import online.youcd.heartrate.data.model.HeartRateZone
import online.youcd.heartrate.ui.theme.WahooTextSecondary

/**
 * 手绘环形图：强度区间分布。
 * 有数据的扇区使用区间色，无数据扇区使用浅灰细弧。
 */
@Composable
fun DonutChart(
    zoneSeconds: List<Int>,
    centerLabel: String,
    centerValue: String,
    modifier: Modifier = Modifier
) {
    val zones = HeartRateZone.ZONES
    val total = zoneSeconds.sum().coerceAtLeast(1)

    Box(modifier = modifier.size(150.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(150.dp)) {
            val strokeWidth = 18.dp.toPx()
            val inset = strokeWidth / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            var startAngle = -90f
            val emptyArc = 360f / zones.size
            zones.forEachIndexed { index, zone ->
                val fraction = zoneSeconds.getOrElse(index) { 0 }.toFloat() / total
                if (fraction > 0f) {
                    drawArc(
                        color = Color(zone.color),
                        startAngle = startAngle,
                        sweepAngle = fraction * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                    startAngle += fraction * 360f
                } else {
                    drawArc(
                        color = Color(zone.color).copy(alpha = 0.14f),
                        startAngle = startAngle,
                        sweepAngle = emptyArc,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(
                            width = strokeWidth * 0.45f,
                            cap = StrokeCap.Butt
                        )
                    )
                    startAngle += emptyArc
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerValue,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
