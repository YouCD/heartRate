package online.youcd.heartrate.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun Sparkline(
    samples: List<Int>,
    modifier: Modifier = Modifier,
    color: Color
) {
    Canvas(modifier = modifier) {
        if (samples.size < 2) return@Canvas
        val min = samples.minOrNull() ?: 0
        val max = samples.maxOrNull() ?: 1
        val range = (max - min).coerceAtLeast(1)

        val points = samples.mapIndexed { index, value ->
            Offset(
                x = size.width * index / (samples.size - 1),
                y = size.height - size.height * (value - min) / range
            )
        }

        val fillPath = Path().apply {
            moveTo(points.first().x, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.3f), Color.Transparent)
            )
        )

        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
