package com.bh6aap.ic705Cter.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import android.widget.Toast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bh6aap.ic705Cter.data.database.DatabaseHelper
import com.bh6aap.ic705Cter.data.database.entity.StationEntity
import com.bh6aap.ic705Cter.util.LogManager
import com.bh6aap.ic705Cter.util.MaidenheadConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 地面站设置对话框
 * 支持配置六位QTH定位符、经纬度、海拔等参数
 * 提供两种保存方案：覆盖默认 / 添加到库
 */
@Composable
fun StationSettingsDialog(
    onDismiss: () -> Unit,
    onStationSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dbHelper = remember { DatabaseHelper.getInstance(context) }

    // 当前地面站数据
    var currentStation by remember { mutableStateOf<StationEntity?>(null) }

    // 输入字段
    var stationName by remember { mutableStateOf("") }
    var callsign by remember { mutableStateOf("") }
    var qthLocator by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var altitude by remember { mutableStateOf("") }

    // 错误提示
    var qthError by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 保存模式：true=覆盖默认, false=添加到库
    var saveAsDefault by remember { mutableStateOf(true) }

    // 加载当前地面站数据和呼号设置
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            // 加载地面站数据
            val station = dbHelper.getDefaultStation()
            // 加载呼号设置
            val savedCallsign = dbHelper.getCallsign()

            withContext(Dispatchers.Main) {
                currentStation = station
                station?.let {
                    stationName = it.name
                    latitude = String.format("%.6f", it.latitude)
                    longitude = String.format("%.6f", it.longitude)
                    altitude = String.format("%.1f", it.altitude)
                    // 计算QTH定位符
                    qthLocator = MaidenheadConverter.latLonToMaidenhead(it.latitude, it.longitude, 6)
                }
                // 加载呼号
                callsign = savedCallsign ?: ""
            }
        }
    }

    // QTH输入变化时自动计算经纬度
    LaunchedEffect(qthLocator) {
        if (qthLocator.length == 6 && MaidenheadConverter.isValidMaidenhead(qthLocator)) {
            val coords = MaidenheadConverter.maidenheadToLatLon(qthLocator)
            if (coords != null) {
                latitude = String.format("%.6f", coords[0])
                longitude = String.format("%.6f", coords[1])
                qthError = null
            }
        } else if (qthLocator.length >= 6) {
            qthError = "无效的QTH定位符格式"
        } else {
            qthError = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "地面站设置",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 输入表单
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 地面站名称
                    OutlinedTextField(
                        value = stationName,
                        onValueChange = { stationName = it },
                        label = { Text("地面站名称 *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // 呼号
                    OutlinedTextField(
                        value = callsign,
                        onValueChange = {
                            callsign = it.uppercase()
                        },
                        label = { Text("呼号 (用于CW预设)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("例如: BA1AA, 用于CW预设中的 <cl> 替换") }
                    )

                    // QTH定位符（6位）
                    OutlinedTextField(
                        value = qthLocator,
                        onValueChange = {
                            qthLocator = it.uppercase().take(6)
                        },
                        label = { Text("QTH定位符 (6位) *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            if (qthError != null) {
                                Text(
                                    text = qthError!!,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (qthLocator.length == 6) {
                                Text("格式正确，已自动计算经纬度")
                            } else {
                                Text("例如: OM89AT")
                            }
                        }
                    )

                    // 纬度
                    OutlinedTextField(
                        value = latitude,
                        onValueChange = { latitude = it },
                        label = { Text("纬度 (°) *") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        supportingText = { Text("范围: -90.0 ~ 90.0") }
                    )

                    // 经度
                    OutlinedTextField(
                        value = longitude,
                        onValueChange = { longitude = it },
                        label = { Text("经度 (°) *") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        supportingText = { Text("范围: -180.0 ~ 180.0") }
                    )

                    // 海拔
                    OutlinedTextField(
                        value = altitude,
                        onValueChange = { altitude = it },
                        label = { Text("海拔 (米)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )

                    // 保存模式选择
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "保存方式",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )

                        // 覆盖默认选项
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { saveAsDefault = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = saveAsDefault,
                                onClick = { saveAsDefault = true }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = "设为默认地面站",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "替换当前默认地面站，用于卫星跟踪",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // 添加到库选项
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { saveAsDefault = false },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !saveAsDefault,
                                onClick = { saveAsDefault = false }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = "添加到地面站库",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "保存到库中，可在主界面长按GPS栏切换",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 错误信息
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }

                    Button(
                        onClick = {
                            // 验证输入
                            when {
                                stationName.isBlank() -> errorMessage = "请输入地面站名称"
                                qthLocator.length != 6 -> errorMessage = "QTH定位符必须是6位"
                                !MaidenheadConverter.isValidMaidenhead(qthLocator) -> errorMessage = "QTH定位符格式无效"
                                latitude.isBlank() -> errorMessage = "请输入纬度"
                                longitude.isBlank() -> errorMessage = "请输入经度"
                                else -> {
                                    try {
                                        val lat = latitude.toDouble()
                                        val lon = longitude.toDouble()
                                        val alt = altitude.toDoubleOrNull() ?: 0.0

                                        if (lat < -90.0 || lat > 90.0) {
                                            errorMessage = "纬度超出有效范围"
                                            return@Button
                                        }
                                        if (lon < -180.0 || lon > 180.0) {
                                            errorMessage = "经度超出有效范围"
                                            return@Button
                                        }

                                        scope.launch(Dispatchers.IO) {
                                            // 检查名称是否被其他地面站使用
                                            val currentId = currentStation?.id
                                            if (dbHelper.isStationNameExists(stationName, currentId)) {
                                                withContext(Dispatchers.Main) {
                                                    errorMessage = "地面站名称 '$stationName' 已被使用，请使用其他名称"
                                                }
                                                return@launch
                                            }

                                            val station = StationEntity(
                                                id = currentStation?.id ?: 0,
                                                name = stationName,
                                                latitude = lat,
                                                longitude = lon,
                                                altitude = alt,
                                                isDefault = saveAsDefault,
                                                notes = "QTH: $qthLocator",
                                                updatedAt = System.currentTimeMillis()
                                            )

                                            if (saveAsDefault) {
                                                // 设为默认：清除其他默认地面站，然后插入/更新
                                                if (currentStation != null && currentStation!!.id > 0) {
                                                    // 更新现有记录
                                                    dbHelper.updateStation(station)
                                                    LogManager.i("StationSettings", "更新默认地面站: ${station.name}")
                                                } else {
                                                    // 新建默认地面站
                                                    dbHelper.clearDefaultStation()
                                                    dbHelper.insertStation(station)
                                                    LogManager.i("StationSettings", "新建默认地面站: ${station.name}")
                                                }
                                            } else {
                                                // 添加到库：新建记录（非默认）
                                                val newStation = station.copy(id = 0, isDefault = false)
                                                dbHelper.insertStation(newStation)
                                                LogManager.i("StationSettings", "添加地面站到库: ${station.name}")
                                            }

                                            // 保存呼号设置
                                            if (callsign.isNotBlank()) {
                                                dbHelper.saveCallsign(callsign)
                                                LogManager.i("StationSettings", "保存呼号: $callsign")
                                            }

                                            withContext(Dispatchers.Main) {
                                                val message = if (saveAsDefault) {
                                                    "已设为默认地面站"
                                                } else {
                                                    "已添加到地面站库"
                                                }
                                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                onStationSaved()
                                                onDismiss()
                                            }
                                        }
                                    } catch (e: NumberFormatException) {
                                        errorMessage = "请输入有效的数字"
                                    }
                                }
                            }
                        }
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
