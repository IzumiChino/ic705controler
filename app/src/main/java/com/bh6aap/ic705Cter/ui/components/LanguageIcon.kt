package com.bh6aap.ic705Cter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bh6aap.ic705Cter.ui.theme.Ic705controlerTheme

/**
 * 自定义语言图标
 * 绘制一个地球/语言符号图标
 */
@Composable
fun LanguageIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Canvas(
        modifier = modifier.size(24.dp)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val centerX = canvasWidth / 2
        val centerY = canvasHeight / 2
        val radius = canvasWidth * 0.4f
        val strokeWidth = canvasWidth * 0.08f

        // 外圆
        drawCircle(
            color = tint,
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = strokeWidth)
        )

        // 垂直椭圆（经线）
        drawOval(
            color = tint,
            topLeft = Offset(centerX - radius * 0.3f, centerY - radius),
            size = androidx.compose.ui.geometry.Size(radius * 0.6f, radius * 2),
            style = Stroke(width = strokeWidth)
        )

        // 水平线（纬线）
        drawLine(
            color = tint,
            start = Offset(centerX - radius * 0.85f, centerY),
            end = Offset(centerX + radius * 0.85f, centerY),
            strokeWidth = strokeWidth
        )

        // 上方弧线
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(centerX - radius * 0.6f, centerY - radius * 0.6f),
            size = androidx.compose.ui.geometry.Size(radius * 1.2f, radius * 1.2f),
            style = Stroke(width = strokeWidth)
        )

        // 下方弧线
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(centerX - radius * 0.6f, centerY - radius * 0.6f),
            size = androidx.compose.ui.geometry.Size(radius * 1.2f, radius * 1.2f),
            style = Stroke(width = strokeWidth)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LanguageIconPreview() {
    Ic705controlerTheme {
        LanguageIcon()
    }
}
