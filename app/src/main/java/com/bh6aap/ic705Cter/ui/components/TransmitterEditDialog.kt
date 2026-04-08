package com.bh6aap.ic705Cter.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bh6aap.ic705Cter.data.api.Transmitter
import com.bh6aap.ic705Cter.data.database.DatabaseHelper
import com.bh6aap.ic705Cter.data.database.entity.CustomTransmitterEntity
import com.bh6aap.ic705Cter.util.LogManager
import com.bh6aap.ic705Cter.R
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext

/**
 * 转发器编辑弹窗
 * 允许用户自定义转发器的频率和描述
 */
@Composable
fun TransmitterEditDialog(
    transmitter: Transmitter,
    dbHelper: DatabaseHelper,
    onDismiss: () -> Unit,
    onSave: (Transmitter) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 输入状态
    var description by remember { mutableStateOf(transmitter.description) }
    var downlinkLow by remember { mutableStateOf(transmitter.downlinkLow?.let { String.format("%.6f", it / 1_000_000.0) } ?: "") }
    var downlinkHigh by remember { mutableStateOf(transmitter.downlinkHigh?.let { String.format("%.6f", it / 1_000_000.0) } ?: "") }
    var uplinkLow by remember { mutableStateOf(transmitter.uplinkLow?.let { String.format("%.6f", it / 1_000_000.0) } ?: "") }
    var uplinkHigh by remember { mutableStateOf(transmitter.uplinkHigh?.let { String.format("%.6f", it / 1_000_000.0) } ?: "") }
    var downlinkMode by remember { mutableStateOf(transmitter.mode) }
    var uplinkMode by remember { mutableStateOf(transmitter.uplinkMode ?: transmitter.mode) }

    // 错误信息
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.transmitter_edit_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 描述输入
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.transmitter_edit_description)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                // 下行频率范围
                Text(
                    text = stringResource(R.string.transmitter_edit_downlink_freq),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = downlinkLow,
                        onValueChange = {
                            downlinkLow = it.filter { char -> char.isDigit() || char == '.' }
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.transmitter_edit_low)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = downlinkHigh,
                        onValueChange = {
                            downlinkHigh = it.filter { char -> char.isDigit() || char == '.' }
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.transmitter_edit_high)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                // 上行频率范围
                Text(
                    text = stringResource(R.string.transmitter_edit_uplink_freq),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uplinkLow,
                        onValueChange = {
                            uplinkLow = it.filter { char -> char.isDigit() || char == '.' }
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.transmitter_edit_low)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = uplinkHigh,
                        onValueChange = {
                            uplinkHigh = it.filter { char -> char.isDigit() || char == '.' }
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.transmitter_edit_high)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                // 下行模式输入
                OutlinedTextField(
                    value = downlinkMode,
                    onValueChange = { downlinkMode = it.uppercase() },
                    label = { Text(stringResource(R.string.transmitter_edit_downlink_mode)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                // 上行模式输入
                OutlinedTextField(
                    value = uplinkMode,
                    onValueChange = { uplinkMode = it.uppercase() },
                    label = { Text(stringResource(R.string.transmitter_edit_uplink_mode)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                // 错误信息
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // 验证输入
                    val downlinkLowHz = downlinkLow.toDoubleOrNull()?.let { (it * 1_000_000).toLong() }
                    val downlinkHighHz = downlinkHigh.toDoubleOrNull()?.let { (it * 1_000_000).toLong() }
                    val uplinkLowHz = uplinkLow.toDoubleOrNull()?.let { (it * 1_000_000).toLong() }
                    val uplinkHighHz = uplinkHigh.toDoubleOrNull()?.let { (it * 1_000_000).toLong() }

                    if (description.isBlank()) {
                        errorMessage = context.getString(R.string.transmitter_edit_error_description)
                        return@Button
                    }

                    // 创建自定义转发器实体并保存到数据库
                    val customEntity = CustomTransmitterEntity(
                        uuid = transmitter.uuid,
                        noradCatId = transmitter.noradCatId,
                        description = description,
                        uplinkLow = uplinkLowHz,
                        uplinkHigh = uplinkHighHz,
                        downlinkLow = downlinkLowHz,
                        downlinkHigh = downlinkHighHz,
                        downlinkMode = downlinkMode,
                        uplinkMode = uplinkMode
                    )

                    scope.launch {
                        try {
                            dbHelper.insertOrUpdateCustomTransmitter(customEntity)

                            // 创建更新后的转发器对象
                            val updatedTransmitter = transmitter.copy(
                                description = description,
                                downlinkLow = downlinkLowHz,
                                downlinkHigh = downlinkHighHz,
                                uplinkLow = uplinkLowHz,
                                uplinkHigh = uplinkHighHz,
                                mode = downlinkMode,
                                uplinkMode = uplinkMode
                            )

                            onSave(updatedTransmitter)
                        } catch (e: Exception) {
                            LogManager.e("SatelliteTracking", "保存转发器自定义数据失败", e)
                            errorMessage = context.getString(R.string.transmitter_edit_save_failed, e.message ?: "")
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(16.dp)
    )
}
