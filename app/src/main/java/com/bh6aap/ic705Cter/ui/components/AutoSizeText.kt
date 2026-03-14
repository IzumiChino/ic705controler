package com.bh6aap.ic705Cter.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 自适应字体大小文本组件
 * 根据可用空间自动调整字体大小
 */
@Composable
fun AutoSizeText(
    text: String,
    color: Color,
    fontWeight: FontWeight,
    maxFontSize: TextUnit = 28.sp,
    minFontSize: TextUnit = 14.sp,
    modifier: Modifier = Modifier
) {
    var fontSize by remember { mutableStateOf(maxFontSize) }
    var readyToDraw by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier) {
        Text(
            text = text,
            color = color,
            fontWeight = fontWeight,
            fontSize = fontSize,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { result ->
                if (result.didOverflowWidth) {
                    if (fontSize > minFontSize) {
                        fontSize = TextUnit(
                            (fontSize.value - 1).coerceAtLeast(minFontSize.value),
                            fontSize.type
                        )
                    }
                } else {
                    readyToDraw = true
                }
            }
        )
    }
}
