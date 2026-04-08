package com.bh6aap.ic705Cter.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bh6aap.ic705Cter.data.api.Transmitter
import com.bh6aap.ic705Cter.tracking.SatelliteTrackingController
import com.bh6aap.ic705Cter.util.LogManager
import com.bh6aap.ic705Cter.util.formatFrequencyWithoutUnit
import com.bh6aap.ic705Cter.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Frequency display panel component
 * Shows satellite frequency on the left, radio frequency on the right
 * Supports collapse/expand and layout switching
 */
@Composable
fun FrequencyDisplayPanel(
    trackingController: SatelliteTrackingController,
    isTracking: Boolean,
    selectedTransmitter: Transmitter?,
    onToggleLayout: () -> Unit,
    isCompactLayout: Boolean = false,
    onDoubleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var frequencyData by remember { mutableStateOf(trackingController.getCurrentFrequencyData()) }
    var isExpanded by remember { mutableStateOf(true) }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isCompactLayout) 180f else 0f,
        animationSpec = tween(durationMillis = 300)
    )
    val isCustomMode by trackingController.isCustomMode.collectAsState()

    LaunchedEffect(Unit) {
        while (isActive) {
            frequencyData = trackingController.getCurrentFrequencyData()
            delay(500)
        }
    }

    val isLinearTransmitter = selectedTransmitter?.downlinkHigh != null &&
            selectedTransmitter.downlinkHigh != selectedTransmitter.downlinkLow

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(
                if (isCustomMode) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (isCustomMode) Color.White else MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = if (isCustomMode) 0.dp else 4.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Title bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tracking_frequency_display),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) stringResource(R.string.common_collapse) else stringResource(R.string.common_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Row(
                    modifier = if (isLinearTransmitter && onDoubleTap != null) {
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { onDoubleTap() })
                            }
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Satellite frequency
                    Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.tracking_satellite_frequency),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FrequencyRow(
                                arrow = "↓",
                                frequency = formatFrequencyWithoutUnit(frequencyData.satelliteDownlink),
                                label = stringResource(R.string.tracking_satellite_label) + " " + stringResource(R.string.tracking_downlink),
                                arrowColor = MaterialTheme.colorScheme.primary
                            )
                            FrequencyRow(
                                arrow = "↑",
                                frequency = formatFrequencyWithoutUnit(frequencyData.satelliteUplink),
                                label = stringResource(R.string.tracking_satellite_label) + " " + stringResource(R.string.tracking_uplink),
                                arrowColor = MaterialTheme.colorScheme.secondary
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier
                                .width(1.dp)
                                .height(80.dp)
                                .padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Right: Radio frequency
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.tracking_radio_frequency),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FrequencyRow(
                                arrow = "↓",
                                frequency = formatFrequencyWithoutUnit(frequencyData.groundDownlink),
                                label = stringResource(R.string.tracking_receive_frequency),
                                arrowColor = MaterialTheme.colorScheme.primary
                            )
                            FrequencyRow(
                                arrow = "↑",
                                frequency = formatFrequencyWithoutUnit(frequencyData.groundUplink),
                                label = stringResource(R.string.tracking_transmit_frequency),
                                arrowColor = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    LogManager.i("SatelliteTracking", "点击切换布局按钮")
                    onToggleLayout()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.tracking_layout_switch),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = rotationAngle }
                )
            }
        }
    }
}

@Composable
private fun FrequencyRow(
    arrow: String,
    frequency: String,
    label: String,
    arrowColor: androidx.compose.ui.graphics.Color
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = arrow,
                style = MaterialTheme.typography.bodyLarge,
                color = arrowColor
            )
            AutoSizeText(
                text = frequency,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxFontSize = 28.sp,
                minFontSize = 14.sp
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
