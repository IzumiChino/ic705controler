package com.bh6aap.ic705Cter.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bh6aap.ic705Cter.tracking.SatelliteTracker
import com.bh6aap.ic705Cter.tracking.TrajectoryPoint
import kotlin.math.*

import com.bh6aap.ic705Cter.ui.theme.*
import com.bh6aap.ic705Cter.R
import androidx.compose.ui.res.stringResource


/**
 * Satellite radar view component (inspired by look4sat)
 * Uses spherical to Cartesian coordinates, Path for trajectory drawing
 * Supports manual rotation fine-tuning
 */
@Composable
fun SatelliteRadarView(
    satellitePositions: List<SatelliteTracker.SatellitePosition>,
    phoneAzimuth: Float,
    phonePitch: Float = 0f,
    satelliteTrajectories: Map<String, List<TrajectoryPoint>> = emptyMap(),
    currentTime: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier,
    onSatelliteClick: ((SatelliteTracker.SatellitePosition) -> Unit)? = null,
    manualOffset: Float = 0f,  // Manual rotation offset (degrees)
    onManualOffsetChange: ((Float) -> Unit)? = null,  // Offset change callback
    enableManualRotate: Boolean = true  // Whether to enable manual rotation
) {
    val textMeasurer = rememberTextMeasurer()
    val radarColor = MaterialTheme.colorScheme.secondary
    val trackColor = MaterialTheme.colorScheme.primary
    val aimColor = MaterialTheme.colorScheme.error
    val errorColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outline

    // 选中的卫星
    var selectedSatellite by remember { mutableStateOf<SatelliteTracker.SatellitePosition?>(null) }

    // 脉冲动画
    val animTransition = rememberInfiniteTransition(label = "animScale")
    val animScale = animTransition.animateFloat(
        initialValue = 16f,
        targetValue = 64f,
        animationSpec = infiniteRepeatable(tween(1000)),
        label = "animScale"
    )

    // Accumulated manual rotation offset
    var accumulatedOffset by remember { mutableFloatStateOf(manualOffset) }

    // Fine-tune toggle state
    var isFineTuneEnabled by remember { mutableStateOf(false) }

    // 同步外部传入的偏移量
    LaunchedEffect(manualOffset) {
        accumulatedOffset = manualOffset
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Fine-tune control button at top-left
        if (enableManualRotate) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                FineTuneControl(
                    offset = accumulatedOffset,
                    isEnabled = isFineTuneEnabled,
                    onToggle = { isFineTuneEnabled = !isFineTuneEnabled },
                    onReset = {
                        accumulatedOffset = 0f
                        onManualOffsetChange?.invoke(0f)
                    }
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val canvasCenter = Offset(this.size.width / 2f, this.size.height / 2f)
                        val canvasRadius = (min(this.size.width, this.size.height) / 2f) * 0.95f

                        // 考虑手动旋转偏移后的点击检测
                        val adjustedAzimuth = { azimuth: Double ->
                            val adjusted = Math.toDegrees(azimuth) - accumulatedOffset
                            Math.toRadians(adjusted)
                        }

                        satellitePositions.forEach { sat ->
                            val satPos = sph2Cart(
                                azimuth = adjustedAzimuth(Math.toRadians(sat.azimuth)),
                                elevation = Math.toRadians(sat.elevation),
                                radius = canvasRadius.toDouble(),
                                center = canvasCenter
                            )
                            val distance = sqrt(
                                (offset.x - satPos.x).pow(2) +
                                (offset.y - satPos.y).pow(2)
                            )
                            if (distance < 30.dp.toPx()) {
                                selectedSatellite = sat
                                onSatelliteClick?.invoke(sat)
                            }
                        }
                    }
                }
                .pointerInput(enableManualRotate, isFineTuneEnabled) {
                    // Only allow dragging when manual rotation and fine-tune are enabled
                    if (!enableManualRotate || !isFineTuneEnabled) return@pointerInput

                    var startAngle = 0f
                    var currentOffset = 0f

                    detectDragGestures(
                        onDragStart = { offset ->
                            val centerX = this.size.width / 2f
                            val centerY = this.size.height / 2f
                            startAngle = atan2(
                                offset.y - centerY,
                                offset.x - centerX
                            ) * 180f / PI.toFloat()
                            currentOffset = accumulatedOffset
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val centerX = this.size.width / 2f
                            val centerY = this.size.height / 2f
                            val currentAngle = atan2(
                                change.position.y - centerY,
                                change.position.x - centerX
                            ) * 180f / PI.toFloat()

                            val deltaAngle = currentAngle - startAngle
                            accumulatedOffset = currentOffset + deltaAngle

                            // 归一化到 -180 ~ 180 度
                            while (accumulatedOffset > 180f) accumulatedOffset -= 360f
                            while (accumulatedOffset < -180f) accumulatedOffset += 360f

                            onManualOffsetChange?.invoke(accumulatedOffset)
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (min(size.width, size.height) / 2f) * 0.95f

            // 根据手机方位角和手动偏移旋转整个雷达图
            rotate(degrees = -phoneAzimuth + accumulatedOffset) {
                // 绘制雷达网格
                drawRadarGrid(
                    center = center,
                    radius = radius,
                    color = radarColor,
                    strokeWidth = 4f,
                    circles = 3
                )

                // 绘制仰角信息
                drawElevationInfo(
                    center = center,
                    radius = radius,
                    color = trackColor,
                    measurer = textMeasurer,
                    circles = 3
                )

                // 绘制卫星轨迹（使用Path）
                satelliteTrajectories.forEach { (_, trajectory) ->
                    // 判断卫星是否正在过境（轨迹中包含当前时间附近的点）
                    val isCurrentPass = trajectory.any {
                        val timeDiff = abs(it.timestamp - currentTime)
                        timeDiff <= 5 * 60 * 1000 // ±5分钟内
                    }
                    
                    // 设置轨迹颜色（适配夜间模式）
                    val trajectoryColor = if (isCurrentPass) {
                        errorColor // 正在过境的卫星轨迹为错误色（红色）
                    } else {
                        outlineColor // 将来过境的卫星轨迹为轮廓色
                    }
                    
                    drawSatelliteTrack(
                        trajectory = trajectory,
                        center = center,
                        radius = radius,
                        trackColor = trajectoryColor,
                        pathColor = trajectoryColor
                    )
                }

                // 绘制卫星位置
                satellitePositions.forEach { sat ->
                    if (sat.elevation > 0) {
                        drawSatellitePosition(
                            sat = sat,
                            center = center,
                            radius = radius,
                            animRadius = animScale.value,
                            color = trackColor,
                            selectedColor = errorColor,
                            isSelected = sat == selectedSatellite
                        )
                    }
                }
            }

            // 绘制瞄准器（手机指向）- 在旋转外部，根据俯仰角定位
            drawAim(
                elevation = phonePitch,
                center = center,
                radius = radius,
                color = aimColor
            )
        }

    }
}

/**
 * Fine-tune control component (top-left corner)
 * Same style as the azimuth/elevation display on the right
 */
@Composable
private fun FineTuneControl(
    offset: Float,
    isEnabled: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // First row: fine-tune toggle and angle display separated
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fine-tune toggle
            Text(
                text = if (isEnabled) stringResource(R.string.radar_fine_tune_on) else stringResource(R.string.radar_fine_tune_off),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { onToggle() }
                    }
            )
            
            // 角度显示（只要角度不为0就显示，不受开关状态影响）
            if (offset != 0f) {
                Text(
                    text = "${offset.toInt()}°",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Show reset button when fine-tune is enabled
        if (isEnabled) {
            Text(
                text = stringResource(R.string.radar_fine_tune_reset),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { onReset() }
                    }
            )
        }
    }
}

/**
 * 球坐标转笛卡尔坐标（参考look4sat）
 * azimuth: 方位角（弧度）
 * elevation: 仰角（弧度）
 * radius: 雷达图半径
 * 
 * 注意：只处理仰角在[0, PI/2]范围内的点，负仰角（地平线以下）会被限制在边缘
 */
private fun sph2Cart(
    azimuth: Double,
    elevation: Double,
    radius: Double,
    center: Offset
): Offset {
    // 将仰角限制在[0, PI/2]范围内，负仰角（地平线以下）限制为0
    val elevationClamped = elevation.coerceIn(0.0, PI / 2)
    
    // 将仰角映射到半径：90°（天顶）= 0，0°（地平线）= radius
    val r = radius * (PI / 2 - elevationClamped) / (PI / 2)
    
    // 计算坐标
    val x = center.x + (r * cos(PI / 2 - azimuth)).toFloat()
    val y = center.y - (r * sin(PI / 2 - azimuth)).toFloat()
    
    return Offset(x, y)
}

/**
 * Draw radar grid
 */
private fun DrawScope.drawRadarGrid(
    center: Offset,
    radius: Float,
    color: Color,
    strokeWidth: Float,
    circles: Int
) {
    // 绘制同心圆（根据仰角设置不同样式）
    for (i in 0 until circles) {
        val circleRadius = radius - radius / circles.toFloat() * i.toFloat()
        val elevationDeg = (90 / circles) * (circles - i)
        
        // 最外圈(90度)加粗50%且使用黑色，0度线（地平线）加粗，其他线变细变浅色
        val lineWidth = when (elevationDeg) {
            90 -> strokeWidth * 1.5f // 最外圈加粗50%
            0 -> strokeWidth * 2 // 0度线加粗
            else -> strokeWidth * 0.6f // 其他线变细
        }
        val lineColor = when (elevationDeg) {
            90 -> color // 最外圈使用黑色
            0 -> color // 0度线使用原色
            else -> color.copy(alpha = 0.6f) // 其他线变浅色
        }
        
        drawCircle(
            color = lineColor,
            radius = circleRadius,
            center = center,
            style = Stroke(lineWidth)
        )
    }

    // 绘制十字线（变细变浅色）
    drawLine(
        color = color.copy(alpha = 0.6f),
        start = center.copy(x = center.x - radius),
        end = center.copy(x = center.x + radius),
        strokeWidth = strokeWidth * 0.6f
    )
    drawLine(
        color = color.copy(alpha = 0.6f),
        start = center.copy(y = center.y - radius),
        end = center.copy(y = center.y + radius),
        strokeWidth = strokeWidth * 0.6f
    )
}

/**
 * Draw elevation info
 */
private fun DrawScope.drawElevationInfo(
    center: Offset,
    radius: Float,
    color: Color,
    measurer: androidx.compose.ui.text.TextMeasurer,
    circles: Int
) {
    for (i in 0 until circles) {
        val textY = (radius - radius / circles * i) - 32f
        val textDeg = " ${(90 / circles) * (circles - i)}°"
        drawText(
            textMeasurer = measurer,
            text = textDeg,
            topLeft = center.copy(y = textY),
            style = TextStyle(color = color, fontSize = 15.sp)
        )
    }
}

/**
 * Draw satellite track (using Path)
 * Includes direction arrows indicating satellite movement
 */
private fun DrawScope.drawSatelliteTrack(
    trajectory: List<TrajectoryPoint>,
    center: Offset,
    radius: Float,
    trackColor: Color,
    pathColor: Color
) {
    // 找到第一个和最后一个仰角>=0°的点的索引
    val firstVisibleIndex = trajectory.indexOfFirst { it.elevation >= 0 }
    val lastVisibleIndex = trajectory.indexOfLast { it.elevation >= 0 }
    
    if (firstVisibleIndex == -1 || lastVisibleIndex == -1) return
    if (lastVisibleIndex - firstVisibleIndex < 1) return
    
    // 提取可见点
    val visiblePoints = trajectory.subList(firstVisibleIndex, lastVisibleIndex + 1).toMutableList()
    
    // 创建轨迹Path
    val trackPath = Path()
    
    // 处理起始点：如果第一个可见点不是轨迹起点，插值计算地平线交点
    if (firstVisibleIndex > 0) {
        val prevPoint = trajectory[firstVisibleIndex - 1]
        val currPoint = trajectory[firstVisibleIndex]
        // 线性插值计算仰角=0°时的位置
        val t = prevPoint.elevation / (prevPoint.elevation - currPoint.elevation)
        val interpolatedAzimuth = prevPoint.azimuth + t * (currPoint.azimuth - prevPoint.azimuth)
        val entryPoint = TrajectoryPoint(
            azimuth = interpolatedAzimuth,
            elevation = 0.0,
            timestamp = prevPoint.timestamp + (t * (currPoint.timestamp - prevPoint.timestamp)).toLong()
        )
        visiblePoints.add(0, entryPoint)
    }
    
    // 处理结束点：如果最后一个可见点不是轨迹终点，插值计算地平线交点
    if (lastVisibleIndex < trajectory.size - 1) {
        val currPoint = trajectory[lastVisibleIndex]
        val nextPoint = trajectory[lastVisibleIndex + 1]
        // 线性插值计算仰角=0°时的位置
        val t = currPoint.elevation / (currPoint.elevation - nextPoint.elevation)
        val interpolatedAzimuth = currPoint.azimuth + t * (nextPoint.azimuth - currPoint.azimuth)
        val exitPoint = TrajectoryPoint(
            azimuth = interpolatedAzimuth,
            elevation = 0.0,
            timestamp = currPoint.timestamp + (t * (nextPoint.timestamp - currPoint.timestamp)).toLong()
        )
        visiblePoints.add(exitPoint)
    }

    // 绘制轨迹
    visiblePoints.forEachIndexed { index, point ->
        val pos = sph2Cart(
            azimuth = Math.toRadians(point.azimuth),
            elevation = Math.toRadians(point.elevation),
            radius = radius.toDouble(),
            center = center
        )
        if (index == 0) {
            trackPath.moveTo(pos.x, pos.y)
        } else {
            trackPath.lineTo(pos.x, pos.y)
        }
    }

    // 绘制轨迹线（实线）
    drawPath(
        path = trackPath,
        color = trackColor,
        style = Stroke(width = 6f)
    )

    // 在轨迹开始和结束位置绘制方向箭头
    // 条件：至少需要5个可见点才能绘制箭头
    if (visiblePoints.size >= 5) {
        val arrowSize = 40f
        val arrowColor = Red900

        // 计算箭头位置 - 对称分布，从两端各跳过35%的点
        // 例如100个点：第一个箭头在第35个点，第二个箭头在第65个点
        val skipRatio = 0.35
        val skipCount = max((visiblePoints.size * skipRatio).toInt(), 1)
        val startArrowIndex = skipCount
        val endArrowIndex = visiblePoints.size - 1 - skipCount

        // 确保两个箭头不会重叠且都在有效范围内
        if (endArrowIndex > startArrowIndex && startArrowIndex >= 0 && endArrowIndex < visiblePoints.size) {
            // 计算开始位置的箭头（指向轨迹前进方向）
            val startArrowPos = sph2Cart(
                azimuth = Math.toRadians(visiblePoints[startArrowIndex].azimuth),
                elevation = Math.toRadians(visiblePoints[startArrowIndex].elevation),
                radius = radius.toDouble(),
                center = center
            )
            // 使用下一个点计算方向
            val startNextIndex = min(startArrowIndex + 1, visiblePoints.size - 1)
            val startNextPos = sph2Cart(
                azimuth = Math.toRadians(visiblePoints[startNextIndex].azimuth),
                elevation = Math.toRadians(visiblePoints[startNextIndex].elevation),
                radius = radius.toDouble(),
                center = center
            )
            val startAngle = atan2(startNextPos.y - startArrowPos.y, startNextPos.x - startArrowPos.x)
            drawArrow(
                position = startArrowPos,
                angle = startAngle,
                color = arrowColor,
                size = arrowSize
            )

            // 计算结束位置的箭头（指向轨迹前进方向）
            val endArrowPos = sph2Cart(
                azimuth = Math.toRadians(visiblePoints[endArrowIndex].azimuth),
                elevation = Math.toRadians(visiblePoints[endArrowIndex].elevation),
                radius = radius.toDouble(),
                center = center
            )
            // 使用下一个点计算方向（如果存在）
            val endNextIndex = min(endArrowIndex + 1, visiblePoints.size - 1)
            val endNextPos = sph2Cart(
                azimuth = Math.toRadians(visiblePoints[endNextIndex].azimuth),
                elevation = Math.toRadians(visiblePoints[endNextIndex].elevation),
                radius = radius.toDouble(),
                center = center
            )
            val endAngle = atan2(endNextPos.y - endArrowPos.y, endNextPos.x - endArrowPos.x)
            drawArrow(
                position = endArrowPos,
                angle = endAngle,
                color = arrowColor,
                size = arrowSize
            )
        }
    }
}

/**
 * Draw direction arrow
 * @param position Arrow position
 * @param angle Arrow direction (radians)
 * @param color Arrow color
 * @param size Arrow size
 */
private fun DrawScope.drawArrow(
    position: Offset,
    angle: Float,
    color: Color,
    size: Float
) {
    val arrowPath = Path()

    // 箭头指向角度方向
    val tipX = position.x + size * cos(angle)
    val tipY = position.y + size * sin(angle)

    // 箭头两翼
    val leftWingAngle = angle + 5.0f // 约143度
    val rightWingAngle = angle - 5.0f

    val leftX = position.x + size * 0.6f * cos(leftWingAngle)
    val leftY = position.y + size * 0.6f * sin(leftWingAngle)
    val rightX = position.x + size * 0.6f * cos(rightWingAngle)
    val rightY = position.y + size * 0.6f * sin(rightWingAngle)

    arrowPath.moveTo(tipX, tipY)
    arrowPath.lineTo(leftX, leftY)
    arrowPath.lineTo(rightX, rightY)
    arrowPath.close()

    drawPath(
        path = arrowPath,
        color = color,
        style = Fill
    )
}

/**
 * Draw satellite position
 */
private fun DrawScope.drawSatellitePosition(
    sat: SatelliteTracker.SatellitePosition,
    center: Offset,
    radius: Float,
    animRadius: Float,
    color: Color,
    selectedColor: Color,
    isSelected: Boolean
) {
    val satPos = sph2Cart(
        azimuth = Math.toRadians(sat.azimuth),
        elevation = Math.toRadians(sat.elevation),
        radius = radius.toDouble(),
        center = center
    )

    // 绘制卫星点（带脉冲动画效果，适配夜间模式）
    drawCircle(
        color = if (isSelected) selectedColor else color,
        radius = 16f,
        center = satPos
    )
    drawCircle(
        color = (if (isSelected) selectedColor else color).copy(alpha = 1 - (animRadius / 64f)),
        radius = animRadius,
        center = satPos
    )
}

/**
 * Draw aim indicator (phone pointing direction)
 * Positioned on radar based on pitch angle
 *
 * Radar coordinate system (flat mode):
 * - Center = 90° elevation (zenith)
 * - Edge = 0° elevation (horizon)
 * - Negative angles (below horizon) not displayed
 *
 * Phone sensor coordinate system:
 * - Flat (screen up): pitch ≈ 0°
 * - Top tilted up: pitch > 0
 * - Top tilted down: pitch < 0
 *
 * Mapping:
 * - Phone top pointing up (pitch = -90°) → aim at center (90° elevation)
 * - Phone flat (pitch = 0°) → aim at edge (0° elevation)
 * - Phone top pointing down (pitch = 90°) → locked at edge
 */
private fun DrawScope.drawAim(
    elevation: Float,
    center: Offset,
    radius: Float,
    color: Color
) {
    val size = 36f

    // 将手机俯仰角转换为雷达图仰角
    // 手机pitch: -90°(顶部向上) -> 雷达图: 90°(天顶)
    // 手机pitch: 0°(平放) -> 雷达图: 0°(地平线)
    // 手机pitch > 0°(顶部向下) -> 锁定在边缘
    val radarElevation = when {
        elevation < 0 -> (-elevation).coerceAtMost(90f) // 顶部向上，映射到0-90°
        else -> 0f // 平放或顶部向下，锁定在0°（边缘）
    }

    // 计算准星位置：仰角越大，越靠近中心
    // elev: 0° -> aimRadius = radius (边缘)
    // elev: 90° -> aimRadius = 0 (中心)
    val aimRadius = radius * (1f - radarElevation / 90f)

    // 瞄准器固定在雷达图顶部（0°方位角，即手机顶部指向）
    val aimX = center.x
    val aimY = center.y - aimRadius

    // 绘制十字准星
    drawLine(
        color = Red300,
        start = Offset(aimX - size, aimY),
        end = Offset(aimX + size, aimY),
        strokeWidth = 6f
    )
    drawLine(
        color = Red300,
        start = Offset(aimX, aimY - size),
        end = Offset(aimX, aimY + size),
        strokeWidth = 6f
    )
    drawCircle(
        color = color,
        radius = size / 2,
        center = Offset(aimX, aimY),
        style = Stroke(width = 6f)
    )
}

/**
 * Satellite info card
 */
@Composable
private fun SatelliteInfoCard(satellite: SatelliteTracker.SatellitePosition) {
    val azimuthText = stringResource(R.string.radar_azimuth)
    val elevationText = stringResource(R.string.radar_elevation)
    Column(
        modifier = Modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = satellite.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "$azimuthText: ${satellite.azimuth.toInt()}° | $elevationText: ${satellite.elevation.toInt()}°",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
