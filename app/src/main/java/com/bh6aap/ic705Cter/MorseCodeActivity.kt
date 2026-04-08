package com.bh6aap.ic705Cter

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bh6aap.ic705Cter.data.database.DatabaseHelper
import com.bh6aap.ic705Cter.data.database.entity.CwPresetEntity
import com.bh6aap.ic705Cter.data.radio.BluetoothConnectionManager
import com.bh6aap.ic705Cter.ui.theme.Ic705controlerTheme
import com.bh6aap.ic705Cter.util.LogManager
import com.bh6aap.ic705Cter.util.formatFrequencyWithoutUnit
import com.bh6aap.ic705Cter.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * 摩尔斯电码Activity
 * 分为上下两层：上层解码，下层发射和编码
 */
class MorseCodeActivity : BaseActivity() {

    private val bluetoothConnectionManager = BluetoothConnectionManager.getInstance()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            LogManager.i("MorseCodeActivity", "录音权限已授予")
        } else {
            LogManager.w("MorseCodeActivity", "录音权限被拒绝")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 检查录音权限
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED -> {
                LogManager.i("MorseCodeActivity", "录音权限已拥有")
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        setContent {
            Ic705controlerTheme {
                MorseCodeScreen(
                    onBackClick = { finish() },
                    bluetoothConnectionManager = bluetoothConnectionManager
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // 退出CW页面时重新开启Split模式
        runBlocking {
            val civController = bluetoothConnectionManager.civController.value
            if (civController != null) {
                LogManager.i("MorseCodeActivity", "退出CW页面，重新开启Split模式")
                val success = civController.ensureSplitModeOn()
                if (success) {
                    LogManager.i("MorseCodeActivity", "Split模式已重新开启")
                } else {
                    LogManager.w("MorseCodeActivity", "Split模式重新开启失败")
                }
            }
        }
    }

    companion object {
        /**
         * 启动摩尔斯电码Activity
         */
        fun start(context: Context) {
            val intent = Intent(context, MorseCodeActivity::class.java)
            context.startActivity(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MorseCodeScreen(
    onBackClick: () -> Unit,
    bluetoothConnectionManager: BluetoothConnectionManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // CW预设列表（6个按钮）
    var cwPresets by remember { mutableStateOf(CwPresetEntity.createDefaults()) }
    // CW输入框内容
    var cwInputText by remember { mutableStateOf("") }
    // 编辑预设弹窗状态
    var editingPresetIndex by remember { mutableStateOf<Int?>(null) }
    var editingPresetName by remember { mutableStateOf("") }
    var editingPresetMessage by remember { mutableStateOf("") }
    // 发射记录列表
    var transmitHistory by remember { mutableStateOf<List<TransmitRecord>>(emptyList()) }
    val listState = rememberLazyListState()

    // 频率显示状态
    var vfoAFrequency by remember { mutableStateOf(0L) }
    var vfoBFrequency by remember { mutableStateOf(0L) }
    var isConnected by remember { mutableStateOf(false) }

    // 加载CW预设
    LaunchedEffect(Unit) {
        val dbHelper = DatabaseHelper.getInstance(context)
        try {
            val savedPresets = dbHelper.getAllCwPresets().first()
            if (savedPresets.isNotEmpty()) {
                // 合并数据库预设和默认值，确保6个按钮都有值
                val mergedPresets = CwPresetEntity.createDefaults().toMutableList()
                savedPresets.forEach { saved ->
                    if (saved.presetIndex in 0..5) {
                        mergedPresets[saved.presetIndex] = saved
                    }
                }
                cwPresets = mergedPresets
                LogManager.i("MorseCodeActivity", "从数据库加载CW预设: ${savedPresets.size}条")
            } else {
                // 数据库为空，使用默认值并保存到数据库
                cwPresets = CwPresetEntity.createDefaults()
                dbHelper.insertCwPresets(cwPresets)
                LogManager.i("MorseCodeActivity", "初始化CW预设到数据库")
            }
        } catch (e: Exception) {
            LogManager.e("MorseCodeActivity", "加载CW预设失败", e)
            cwPresets = CwPresetEntity.createDefaults()
        }
    }

    // 页面加载时：关闭Split模式并读取频率
    LaunchedEffect(Unit) {
        // 关闭Split模式
        launch {
            val civController = bluetoothConnectionManager.civController.value
            if (civController != null) {
                LogManager.i("MorseCodeActivity", "进入CW页面，关闭Split模式")
                val success = civController.ensureSplitModeOff()
                if (success) {
                    LogManager.i("MorseCodeActivity", "Split模式已关闭")
                } else {
                    LogManager.w("MorseCodeActivity", "Split模式关闭失败")
                }
            }
        }

        // 监听连接状态
        launch {
            bluetoothConnectionManager.connectionState.collect { state ->
                isConnected = state == com.bh6aap.ic705Cter.data.radio.BluetoothSppConnector.ConnectionState.CONNECTED
                LogManager.i("MorseCodeActivity", "连接状态变化: $state")
            }
        }

        // 监听VFO-A频率
        launch {
            bluetoothConnectionManager.vfoAFrequency.collect { freqStr ->
                if (freqStr != "-") {
                    vfoAFrequency = freqStr.replace(".", "").toLongOrNull() ?: 0L
                    LogManager.d("MorseCodeActivity", "VFO-A频率更新: $freqStr")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.morse_code_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 频率显示区域（计算器样式）
            FrequencyDisplayCard(
                vfoAFrequency = vfoAFrequency,
                vfoBFrequency = vfoBFrequency,
                isConnected = isConnected,
                modifier = Modifier.fillMaxWidth()
            )

            // CW发射区域
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 标题和连接状态
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.morse_code_cw_transmission),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        // 连接状态指示
                        ConnectionStatusIndicator(isConnected = isConnected)
                    }

                    // CW模块卡片
                    CwModuleCard(
                        presets = cwPresets,
                        inputText = cwInputText,
                        onInputTextChange = { cwInputText = it },
                        isConnected = isConnected,
                        onPresetClick = { index ->
                            val preset = cwPresets.getOrNull(index)
                            if (preset != null && preset.message.isNotEmpty()) {
                                scope.launch {
                                    val civController = bluetoothConnectionManager.civController.value
                                    if (civController != null && isConnected) {
                                        // 先执行强制停止命令
                                        LogManager.i("MorseCodeActivity", "执行强制停止命令")
                                        civController.stopCwTransmission()
                                        
                                        delay(500)
                                        
                                        // 处理 <cl> 替换为呼号
                                        val dbHelper = DatabaseHelper.getInstance(context)
                                        val callsign = dbHelper.getCallsign()
                                        val message = if (callsign != null && preset.message.contains("<cl>")) {
                                            preset.message.replace("<cl>", callsign)
                                        } else {
                                            preset.message
                                        }
                                        
                                        val success = civController.sendCwMessage(message)
                                        if (success) {
                                            LogManager.i("MorseCodeActivity", "CW预设发送成功: ${preset.name} - $message")
                                            // 添加到发射记录
                                            val record = TransmitRecord(
                                                message = message,
                                                timestamp = System.currentTimeMillis(),
                                                source = "预设: ${preset.name}"
                                            )
                                            transmitHistory = listOf(record) + transmitHistory
                                        } else {
                                            LogManager.e("MorseCodeActivity", "CW预设发送失败")
                                        }
                                    } else {
                                        LogManager.w("MorseCodeActivity", "CIV控制器未初始化或未连接")
                                    }
                                }
                            }
                        },
                        onPresetLongClick = { index ->
                            val preset = cwPresets.getOrNull(index)
                            if (preset != null) {
                                editingPresetIndex = index
                                editingPresetName = preset.name
                                editingPresetMessage = preset.message
                            }
                        },
                        onSendClick = {
                            if (cwInputText.isNotEmpty()) {
                                scope.launch {
                                    val civController = bluetoothConnectionManager.civController.value
                                    if (civController != null && isConnected) {
                                        // 处理 <cl> 替换为呼号
                                        val dbHelper = DatabaseHelper.getInstance(context)
                                        val callsign = dbHelper.getCallsign()
                                        val message = if (callsign != null && cwInputText.contains("<cl>")) {
                                            cwInputText.replace("<cl>", callsign)
                                        } else {
                                            cwInputText
                                        }
                                        
                                        val success = civController.sendCwMessage(message)
                                        if (success) {
                                            LogManager.i("MorseCodeActivity", "CW消息发送成功: $message")
                                            // 添加到发射记录
                                            val record = TransmitRecord(
                                                message = message,
                                                timestamp = System.currentTimeMillis(),
                                                source = "手动输入"
                                            )
                                            transmitHistory = listOf(record) + transmitHistory
                                            cwInputText = "" // 发送后清空输入框
                                        } else {
                                            LogManager.e("MorseCodeActivity", "CW消息发送失败")
                                        }
                                    } else {
                                        LogManager.w("MorseCodeActivity", "CIV控制器未初始化或未连接")
                                    }
                                }
                            }
                        },
                        onStopClick = {
                            scope.launch {
                                val civController = bluetoothConnectionManager.civController.value
                                if (civController != null && isConnected) {
                                    val success = civController.stopCwTransmission()
                                    if (success) {
                                        LogManager.i("MorseCodeActivity", "CW发射已停止")
                                    } else {
                                        LogManager.e("MorseCodeActivity", "停止CW发射失败")
                                    }
                                } else {
                                    LogManager.w("MorseCodeActivity", "CIV控制器未初始化或未连接")
                                }
                            }
                        },
                        onClearClick = {
                            cwInputText = "" // 清空输入框
                            LogManager.i("MorseCodeActivity", "清空CW输入框")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 发射记录区域
                    if (transmitHistory.isNotEmpty()) {
                        TransmitHistoryCard(
                            history = transmitHistory,
                            listState = listState,
                            onClearHistory = { transmitHistory = emptyList() },
                            onRecordClick = { record ->
                                // 点击记录填入输入框
                                cwInputText = record.message
                                LogManager.i("MorseCodeActivity", "从历史记录填入: ${record.message}")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // 编辑预设弹窗
    if (editingPresetIndex != null) {
        EditPresetDialog(
            name = editingPresetName,
            message = editingPresetMessage,
            onNameChange = { editingPresetName = it },
            onMessageChange = { editingPresetMessage = it },
            onDismiss = { editingPresetIndex = null },
            onSave = {
                val index = editingPresetIndex
                if (index != null) {
                    // 更新预设列表
                    val updatedPresets = cwPresets.toMutableList()
                    val oldPreset = updatedPresets[index]
                    val newPreset = oldPreset.copy(
                        name = editingPresetName,
                        message = editingPresetMessage
                    )
                    updatedPresets[index] = newPreset
                    cwPresets = updatedPresets
                    // 保存到数据库
                    scope.launch {
                        try {
                            val dbHelper = DatabaseHelper.getInstance(context)
                            dbHelper.insertCwPreset(newPreset)
                            LogManager.i("MorseCodeActivity", "保存CW预设到数据库 $index: ${editingPresetName}")
                        } catch (e: Exception) {
                            LogManager.e("MorseCodeActivity", "保存CW预设失败", e)
                        }
                    }
                }
                editingPresetIndex = null
            }
        )
    }
}

/**
 * 频率显示卡片（简化版，只显示当前频率）
 * 字体大小自适应容器尺寸
 */
@Composable
private fun FrequencyDisplayCard(
    vfoAFrequency: Long,
    vfoBFrequency: Long,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.morse_code_current_frequency),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isConnected) {
                    Text(
                        text = stringResource(R.string.morse_code_not_connected),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // 频率显示区域（自适应字体大小）
            FrequencyDisplayAutoSize(
                frequency = vfoAFrequency,
                isConnected = isConnected,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 自适应字体大小的频率显示
 */
@Composable
private fun FrequencyDisplayAutoSize(
    frequency: Long,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val displayText = if (isConnected && frequency > 0) {
        formatFrequencyWithoutUnit(frequency.toDouble(), precision = 6)
    } else {
        "--.------"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = androidx.compose.foundation.BorderStroke(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            // 根据容器宽度计算字体大小
            val textSize = (maxWidth.value / displayText.length * 1.8f).coerceIn(24f, 48f)

            Text(
                text = displayText,
                fontSize = textSize.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = if (isConnected && frequency > 0) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                maxLines = 1
            )
        }
    }
}

/**
 * 连接状态指示器
 */
@Composable
private fun ConnectionStatusIndicator(isConnected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFE57373),
                    shape = RoundedCornerShape(4.dp)
                )
        )
        Text(
            text = if (isConnected) "已连接" else "未连接",
            style = MaterialTheme.typography.labelSmall,
            color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFE57373)
        )
    }
}

/**
 * CW模块卡片
 * 包含6个预设按钮和输入框
 * 布局：第一行6个预设按钮，第二行输入框+取消+发射
 */
@Composable
private fun CwModuleCard(
    presets: List<CwPresetEntity>,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    isConnected: Boolean,
    onPresetClick: (Int) -> Unit,
    onPresetLongClick: (Int) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 6个预设按钮（单行排列）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (0..5).forEach { index ->
                    val preset = presets.getOrNull(index)
                    if (preset != null) {
                        PresetButton(
                            name = preset.name,
                            message = preset.message,
                            enabled = isConnected,
                            onClick = { onPresetClick(index) },
                            onLongClick = { onPresetLongClick(index) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            HorizontalDivider()

            // 输入框、取消按钮、发射按钮和停止按钮
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 简约细长条输入框
                BasicTextField(
                    value = inputText,
                    onValueChange = { newValue ->
                        if (newValue.length <= 30) {
                            onInputTextChange(newValue)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.morse_code_input_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 取消按钮 - 清除焦点
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            onClearClick()
                        },
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text("取消")
                    }

                    // 发射按钮
                    Button(
                        onClick = onSendClick,
                        enabled = inputText.isNotEmpty() && isConnected,
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text("发射")
                    }

                    // 停止CW按钮
                    Button(
                        onClick = onStopClick,
                        enabled = isConnected,
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text("停止CW")
                    }
                }
            }
        }
    }
}

/**
 * 预设按钮组件
 */
@Composable
private fun PresetButton(
    name: String,
    message: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(48.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (enabled) onClick() },
                    onLongPress = { if (enabled) onLongClick() }
                )
            },
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
        }
    }
}

/**
 * 发射记录数据类
 */
data class TransmitRecord(
    val message: String,
    val timestamp: Long,
    val source: String
)

/**
 * 发射记录卡片
 * 显示滚动的发射历史记录
 */
@Composable
private fun TransmitHistoryCard(
    history: List<TransmitRecord>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onClearHistory: () -> Unit,
    onRecordClick: (TransmitRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.morse_code_transmit_history, history.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = onClearHistory,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = stringResource(R.string.morse_code_clear_history),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 滚动列表
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(history) { record ->
                    TransmitHistoryItem(
                        record = record,
                        timeFormat = timeFormat,
                        onClick = { onRecordClick(record) }
                    )
                }
            }
        }
    }
}

/**
 * 发射记录项（可点击）
 */
@Composable
private fun TransmitHistoryItem(
    record: TransmitRecord,
    timeFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：时间和来源
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeFormat.format(Date(record.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = record.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }

            // 右侧：消息内容
            Text(
                text = record.message,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 编辑预设弹窗
 */
@Composable
private fun EditPresetDialog(
    name: String,
    message: String,
    onNameChange: (String) -> Unit,
    onMessageChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.morse_code_edit_preset),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("按钮名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { newValue ->
                        if (newValue.length <= 30) {
                            onMessageChange(newValue)
                        }
                    },
                    label = { Text("CW消息 (最多30字符)") },
                    placeholder = { Text("例如: CQ CQ DE BG0ABC") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text("${message.length}/30")
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = name.isNotEmpty() && message.isNotEmpty()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
