package com.bh6aap.ic705Cter.ui.components

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
                    
                    // 使用GPS获取位置按钮（暂时隐藏）
                    // IconButton(
                    //     onClick = {
                    //         scope.launch {
                    //             try {
                    //                 // 这里简化处理，实际应该调用GPS管理器
                    //                 Toast.makeText(context, "请使用启动时的GPS定位功能", Toast.LENGTH_SHORT).show()
                    //             } catch (e: Exception) {
                    //                 LogManager.e("StationSettings", "GPS获取失败", e)
                    //             }
                    //         }
                    //     }
                    // ) {
                    //     Icon(
                    //         imageVector = Icons.Default.MyLocation,
                    //         contentDescription = "使用GPS位置"
                    //     )
                    // }
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
                                            val station = StationEntity(
                                                id = currentStation?.id ?: 0,
                                                name = stationName,
                                                latitude = lat,
                                                longitude = lon,
                                                altitude = alt,
                                                isDefault = true,
                                                notes = "QTH: $qthLocator",
                                                updatedAt = System.currentTimeMillis()
                                            )
                                            
                                            if (currentStation != null) {
                                                dbHelper.updateStation(station)
                                                LogManager.i("StationSettings", "更新地面站: ${station.name}")
                                            } else {
                                                dbHelper.insertStation(station)
                                                LogManager.i("StationSettings", "新建地面站: ${station.name}")
                                            }
                                            
                                            // 保存呼号设置
                                            if (callsign.isNotBlank()) {
                                                dbHelper.saveCallsign(callsign)
                                                LogManager.i("StationSettings", "保存呼号: $callsign")
                                            }
                                            
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "地面站和呼号已保存", Toast.LENGTH_SHORT).show()
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
