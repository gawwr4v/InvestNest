package com.gourav.investnest.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gourav.investnest.model.NavPoint

// native canvas is way faster than pulling in a massive chart library for one graph, keeping it lean
@Composable
fun NavChart(
    points: List<NavPoint>, // list of nav values over time to plot on the graph
    modifier: Modifier = Modifier,
) {
    // shows a placeholder if we dont have enough data points to draw a line
    if (points.size < 2) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
        ) {
            Text(
                text = "Not enough NAV history yet.",
                modifier = Modifier.padding(20.dp),
            )
        }
        return
    }

    // calculates the value range to scale the graph vertically
    val minValue = points.minOf { it.nav }
    val maxValue = points.maxOf { it.nav }
    val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
    val chartBackground = MaterialTheme.colorScheme.surfaceVariant
    val chartLineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    // renders a custom line chart using low level drawing operations
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(
                color = chartBackground,
                shape = RoundedCornerShape(20.dp),
            )
            .padding(16.dp),
    ) {
        // draw some simple grid lines so the chart doesn't look like it's floating in space
        val rowCount = 4
        repeat(rowCount) { index ->
            val y = size.height / rowCount * index
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2f,
            )
        }

        // creates a vector path connecting each data point
        val path = Path()
        points.forEachIndexed { index, point ->
            // calculates the horizontal position based on the index
            val x = if (points.size == 1) {
                size.width / 2f
            } else {
                size.width * index / (points.size - 1).toFloat()
            }
            // we normalize the nav value to a 0.0 - 1.0 range based on the min/max
            // then map it to the actual pixel height of the canvas
            val normalized = (point.nav - minValue) / range
            val y = size.height - (normalized * size.height)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        // strokes the final path to show the performance line
        drawPath(
            path = path,
            color = chartLineColor,
            style = Stroke(
                width = 8f,
                cap = StrokeCap.Round,
            ),
        )
    }
}
