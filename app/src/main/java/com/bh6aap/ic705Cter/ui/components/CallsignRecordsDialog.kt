package com.bh6aap.ic705Cter.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bh6aap.ic705Cter.data.CallsignDataStore
import com.bh6aap.ic705Cter.data.CallsignRecord
import com.bh6aap.ic705Cter.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.stringResource

/**
 * 呼号记录对话框
 * 用于查看、编辑和管理卫星跟踪时的呼号记录
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsignRecordsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callsignDataStore = remember { CallsignDataStore(context) }

    var callsignRecords by remember { mutableStateOf<List<CallsignRecord>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedRecord by remember { mutableStateOf<CallsignRecord?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editedCallsign by remember { mutableStateOf("") }
    var editedSatellite by remember { mutableStateOf("") }
    var editedUtcTime by remember { mutableStateOf("") }
    var editedFrequency by remember { mutableStateOf("") }
    var editedMode by remember { mutableStateOf("") }
    var editedGrid by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    // 加载呼号记录
    LaunchedEffect(Unit) {
        isLoading = true
        callsignDataStore.getCallsigns().collect {records ->
            callsignRecords = records
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(0.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 4.dp
                    ) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.callsign_records_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.common_back),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            },
                            actions = {
                                if (callsignRecords.isNotEmpty()) {
                                    IconButton(
                                        onClick = { showClearConfirm = true }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.callsign_records_clear),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(it)
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (callsignRecords.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.callsign_records_empty),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.callsign_records_empty_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(callsignRecords) {
                                CallsignRecordCard(
                                    record = it,
                                    onEdit = {
                                        selectedRecord = it
                                        editedCallsign = it.callsign
                                        editedSatellite = it.satelliteName
                                        editedUtcTime = it.utcTime
                                        editedFrequency = it.frequency
                                        editedMode = it.mode
                                        editedGrid = it.grid
                                        showEditDialog = true
                                    },
                                    onDelete = {
                                        scope.launch {
                                            callsignDataStore.deleteCallsign(it.timestamp)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 编辑对话框
    if (showEditDialog && selectedRecord != null) {
        EditCallsignDialog(
            originalRecord = selectedRecord!!,
            editedCallsign = editedCallsign,
            editedSatellite = editedSatellite,
            editedUtcTime = editedUtcTime,
            editedFrequency = editedFrequency,
            editedMode = editedMode,
            editedGrid = editedGrid,
            onCallsignChange = { editedCallsign = it },
            onSatelliteChange = { editedSatellite = it },
            onUtcTimeChange = { editedUtcTime = it },
            onFrequencyChange = { editedFrequency = it },
            onModeChange = { editedMode = it },
            onGridChange = { editedGrid = it },
            onSave = {
                scope.launch {
                    // 删除旧记录
                    callsignDataStore.deleteCallsign(selectedRecord!!.timestamp)
                    // 保存新记录（保持原时间戳）
                    val updatedRecord = CallsignRecord(
                        callsign = editedCallsign,
                        satelliteName = editedSatellite,
                        timestamp = selectedRecord!!.timestamp,
                        utcTime = editedUtcTime,
                        frequency = editedFrequency,
                        mode = editedMode,
                        grid = editedGrid
                    )
                    callsignDataStore.saveCallsign(updatedRecord)
                }
                showEditDialog = false
                selectedRecord = null
                editedCallsign = ""
                editedSatellite = ""
                editedUtcTime = ""
                editedFrequency = ""
                editedMode = ""
                editedGrid = ""
            },
            onDismiss = {
                showEditDialog = false
                selectedRecord = null
                editedCallsign = ""
                editedSatellite = ""
                editedUtcTime = ""
                editedFrequency = ""
                editedMode = ""
                editedGrid = ""
            }
        )
    }

    // 清空确认对话框
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = {
                Text(
                    text = stringResource(R.string.callsign_records_clear),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.callsign_records_clear_confirm),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        callsignDataStore.clearAll()
                    }
                    showClearConfirm = false
                }) {
                    Text(
                        text = stringResource(R.string.common_ok),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/**
 * 呼号记录卡片 - 窄长条样式，支持滑动查看详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallsignRecordCard(
    record: CallsignRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val formattedTime = remember(record.timestamp) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            .format(Date(record.timestamp))
    }

    // 滑动偏移量
    var offsetX by remember { mutableStateOf(0f) }
    val maxOffset = 120f // 最大滑动距离

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        // 背景层 - 显示操作按钮
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 编辑按钮
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onEdit),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.common_edit),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 删除按钮
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onDelete),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 前景层 - 窄长条卡片
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .offset(x = offsetX.dp)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val newOffset = offsetX + delta
                        offsetX = newOffset.coerceIn(-maxOffset, 0f)
                    },
                    onDragStopped = {
                        // 滑动结束时，根据位置决定是否展开或收起
                        offsetX = if (offsetX < -maxOffset / 2) {
                            -maxOffset
                        } else {
                            0f
                        }
                    }
                )
                .clickable {
                    // 点击时收起
                    offsetX = 0f
                    onEdit()
                },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 呼号 - 突出显示
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = record.callsign,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // 卫星名称
                Text(
                    text = record.satelliteName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                // 右侧信息 - 频率、模式、时间
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (record.frequency.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = record.frequency,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (record.mode.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = record.mode,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // 时间
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 网格（如果有）
                if (record.grid.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = record.grid,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 编辑呼号对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCallsignDialog(
    originalRecord: CallsignRecord,
    editedCallsign: String,
    editedSatellite: String,
    editedUtcTime: String,
    editedFrequency: String,
    editedMode: String,
    editedGrid: String,
    onCallsignChange: (String) -> Unit,
    onSatelliteChange: (String) -> Unit,
    onUtcTimeChange: (String) -> Unit,
    onFrequencyChange: (String) -> Unit,
    onModeChange: (String) -> Unit,
    onGridChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.callsign_records_edit),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 呼号输入
                OutlinedTextField(
                    value = editedCallsign,
                    onValueChange = onCallsignChange,
                    label = {
                        Text(stringResource(R.string.callsign_records_callsign))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                // 卫星名称输入
                OutlinedTextField(
                    value = editedSatellite,
                    onValueChange = onSatelliteChange,
                    label = {
                        Text(stringResource(R.string.callsign_records_satellite))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                // UTC时间输入
                OutlinedTextField(
                    value = editedUtcTime,
                    onValueChange = onUtcTimeChange,
                    label = {
                        Text(stringResource(R.string.callsign_records_time))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                // 频率输入
                OutlinedTextField(
                    value = editedFrequency,
                    onValueChange = onFrequencyChange,
                    label = {
                        Text(stringResource(R.string.callsign_records_frequency))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                // 模式输入
                OutlinedTextField(
                    value = editedMode,
                    onValueChange = onModeChange,
                    label = {
                        Text(stringResource(R.string.callsign_records_mode))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                // 网格输入
                OutlinedTextField(
                    value = editedGrid,
                    onValueChange = onGridChange,
                    label = {
                        Text(stringResource(R.string.callsign_records_grid))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                // 原记录信息
                Text(
                    text = "记录时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        .format(Date(originalRecord.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = editedCallsign.isNotEmpty() && editedSatellite.isNotEmpty()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.common_save),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(stringResource(R.string.callsign_records_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
