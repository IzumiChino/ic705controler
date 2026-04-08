package com.bh6aap.ic705Cter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bh6aap.ic705Cter.data.database.DatabaseHelper
import com.bh6aap.ic705Cter.util.EditableWhitelistManager
import com.bh6aap.ic705Cter.util.LogManager
import com.bh6aap.ic705Cter.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

/**
 * 白名单编辑对话框
 * 支持添加、删除、编辑卫星白名单
 */
@Composable
fun WhitelistEditDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whitelistManager = remember { EditableWhitelistManager.getInstance(context) }

    var entries by remember { mutableStateOf<List<EditableWhitelistManager.WhitelistEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<EditableWhitelistManager.WhitelistEntry?>(null) }
    var stats by remember { mutableStateOf("") }

    // 加载白名单数据
    LaunchedEffect(Unit) {
        scope.launch {
            whitelistManager.initDefaultWhitelistIfNeeded()
            entries = whitelistManager.getAllEntries()
            stats = whitelistManager.getStatistics()
            isLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.whitelist_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = stats,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row {
                        // 重置按钮
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        whitelistManager.resetToDefault()
                                        entries = whitelistManager.getAllEntries()
                                        stats = whitelistManager.getStatistics()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.whitelist_reset_default)
                                )
                            }
                            Text(
                                text = stringResource(R.string.whitelist_reset_default),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 添加按钮
                        IconButton(
                            onClick = { showAddDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.whitelist_add_satellite)
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 白名单列表
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.whitelist_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = entries.sortedBy { it.noradId.toIntOrNull() ?: 0 },
                            key = { it.noradId }
                        ) { entry ->
                            WhitelistItem(
                                entry = entry,
                                onEdit = { editingEntry = entry },
                                onDelete = {
                                    scope.launch {
                                        whitelistManager.removeSatellite(entry.noradId)
                                        entries = whitelistManager.getAllEntries()
                                        stats = whitelistManager.getStatistics()
                                    }
                                }
                            )
                        }
                    }
                }

                // 关闭按钮
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
    }

    // 添加/编辑对话框
    if (showAddDialog || editingEntry != null) {
        AddEditSatelliteDialog(
            entry = editingEntry,
            onDismiss = {
                showAddDialog = false
                editingEntry = null
            },
            onConfirm = { noradId, name, type ->
                scope.launch {
                    if (editingEntry != null) {
                        // 编辑模式
                        whitelistManager.updateSatellite(noradId, name, type)
                    } else {
                        // 添加模式
                        whitelistManager.addSatellite(noradId, name, type)
                    }
                    entries = whitelistManager.getAllEntries()
                    stats = whitelistManager.getStatistics()
                }
                showAddDialog = false
                editingEntry = null
            }
        )
    }
}

/**
 * 白名单条目卡片
 */
@Composable
private fun WhitelistItem(
    entry: EditableWhitelistManager.WhitelistEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (entry.isCustom) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.whitelist_satellite_info, entry.noradId, if (entry.type == EditableWhitelistManager.SatelliteType.FM) stringResource(R.string.whitelist_satellite_type_fm) else stringResource(R.string.whitelist_satellite_type_linear)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.isCustom) {
                    Text(
                        text = stringResource(R.string.common_custom),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.common_edit)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * 搜索结果数据类
 */
private data class SearchResult(
    val noradId: String,
    val name: String,
    val aliases: String? = null  // 别名
)

/**
 * 添加/编辑卫星对话框
 * 简化版：只有一个搜索框，支持ID、名称和别名模糊搜索
 */
@Composable
private fun AddEditSatelliteDialog(
    entry: EditableWhitelistManager.WhitelistEntry?,
    onDismiss: () -> Unit,
    onConfirm: (noradId: String, name: String, type: EditableWhitelistManager.SatelliteType) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dbHelper = remember { DatabaseHelper.getInstance(context) }

    val isEditMode = entry != null

    // 编辑模式使用原有逻辑
    if (isEditMode) {
        EditSatelliteDialog(
            entry = entry!!,
            onDismiss = onDismiss,
            onConfirm = onConfirm
        )
        return
    }

    // 添加模式：简化搜索界面
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedResult by remember { mutableStateOf<SearchResult?>(null) }
    var selectedType by remember { mutableStateOf(EditableWhitelistManager.SatelliteType.FM) }

    // 搜索逻辑（同时搜索TLE数据和卫星信息表）
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && searchQuery.length >= 1) {
            isSearching = true
            delay(300)
            scope.launch {
                try {
                    val whitelistManager = EditableWhitelistManager.getInstance(context)
                    val existingIds = whitelistManager.getAllEntries().map { it.noradId }.toSet()
                    val results = mutableListOf<SearchResult>()

                    // 1. 优先按ID精确查询TLE数据
                    if (searchQuery.all { it.isDigit() }) {
                        val idResult = dbHelper.getSatelliteByNoradId(searchQuery)
                        if (idResult != null && idResult.noradId !in existingIds) {
                            // 查询别名
                            val satInfo = dbHelper.getSatelliteInfoByNoradId(idResult.noradId.toIntOrNull() ?: 0)
                            results.add(SearchResult(
                                noradId = idResult.noradId,
                                name = idResult.name,
                                aliases = satInfo?.names
                            ))
                        }
                    }

                    // 2. 搜索TLE数据（名称）
                    dbHelper.searchSatellites(searchQuery).collect { tleResults ->
                        tleResults.forEach { satellite ->
                            if (satellite.noradId !in existingIds && results.none { it.noradId == satellite.noradId }) {
                                val satInfo = dbHelper.getSatelliteInfoByNoradId(satellite.noradId.toIntOrNull() ?: 0)
                                results.add(SearchResult(
                                    noradId = satellite.noradId,
                                    name = satellite.name,
                                    aliases = satInfo?.names
                                ))
                            }
                        }
                    }

                    // 3. 搜索卫星信息表（名称和别名）
                    dbHelper.searchSatelliteInfo(searchQuery).collect { infoResults ->
                        infoResults.forEach { info ->
                            val noradIdStr = info.noradCatId.toString()
                            if (noradIdStr !in existingIds && results.none { it.noradId == noradIdStr }) {
                                // 获取TLE数据中的名称（优先使用TLE的名称）
                                val tleSatellite = dbHelper.getSatelliteByNoradId(noradIdStr)
                                results.add(SearchResult(
                                    noradId = noradIdStr,
                                    name = tleSatellite?.name ?: info.name,
                                    aliases = info.names
                                ))
                            }
                        }
                    }

                    searchResults = results
                    LogManager.d("AddEditSatelliteDialog", "搜索完成，找到 ${results.size} 个结果")
                } catch (e: Exception) {
                    LogManager.e("AddEditSatelliteDialog", "搜索失败: ${e.message}", e)
                } finally {
                    isSearching = false
                }
            }
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    // 选择卫星后检测类型
    LaunchedEffect(selectedResult) {
        selectedResult?.let { result ->
            selectedType = detectSatelliteTypeByTransmitters(dbHelper, result.noradId)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whitelist_add_satellite)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp, max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        selectedResult = null
                    },
                    label = { Text(stringResource(R.string.whitelist_search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text(stringResource(R.string.whitelist_search_supporting)) },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.common_search)
                            )
                        }
                    },
                    singleLine = true
                )

                // 搜索结果列表
                if (searchResults.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.whitelist_search_results, searchResults.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        LazyColumn {
                            items(searchResults) { result ->
                                val isSelected = selectedResult?.noradId == result.noradId
                                SatelliteSearchResultItemWithAlias(
                                    result = result,
                                    isSelected = isSelected,
                                    onClick = {
                                        selectedResult = result
                                    }
                                )
                            }
                        }
                    }
                } else if (searchQuery.isNotBlank() && !isSearching && searchQuery.length >= 2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.whitelist_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (searchQuery.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.whitelist_search_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 显示已选择的卫星信息
                selectedResult?.let { result ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.whitelist_selected, result.name),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(
                                    R.string.whitelist_satellite_info,
                                    result.noradId,
                                    if (selectedType == EditableWhitelistManager.SatelliteType.FM)
                                        stringResource(R.string.whitelist_satellite_type_fm)
                                    else
                                        stringResource(R.string.whitelist_satellite_type_linear)
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedResult?.let { result ->
                        onConfirm(result.noradId, result.name, selectedType)
                    }
                },
                enabled = selectedResult != null
            ) {
                Text(stringResource(R.string.common_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * 编辑卫星对话框（保持原有逻辑）
 */
@Composable
private fun EditSatelliteDialog(
    entry: EditableWhitelistManager.WhitelistEntry,
    onDismiss: () -> Unit,
    onConfirm: (noradId: String, name: String, type: EditableWhitelistManager.SatelliteType) -> Unit
) {
    var name by remember { mutableStateOf(entry.name) }
    var selectedType by remember { mutableStateOf(entry.type) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.whitelist_edit_satellite)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 显示ID（不可编辑）
                OutlinedTextField(
                    value = entry.noradId,
                    onValueChange = { },
                    label = { Text(stringResource(R.string.whitelist_norad_id)) },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )

                // 名称编辑
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.whitelist_satellite_name)) },
                    modifier = Modifier.fillMaxWidth()
                )

                // 类型选择
                Text(
                    text = stringResource(R.string.whitelist_satellite_type),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == EditableWhitelistManager.SatelliteType.FM,
                        onClick = { selectedType = EditableWhitelistManager.SatelliteType.FM },
                        label = { Text(stringResource(R.string.whitelist_type_fm)) }
                    )
                    FilterChip(
                        selected = selectedType == EditableWhitelistManager.SatelliteType.LINEAR,
                        onClick = { selectedType = EditableWhitelistManager.SatelliteType.LINEAR },
                        label = { Text(stringResource(R.string.whitelist_type_linear)) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(entry.noradId, name, selectedType)
                    }
                }
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * 卫星搜索结果项（带别名）
 * 显示格式：主名称 (别名)
 */
@Composable
private fun SatelliteSearchResultItemWithAlias(
    result: SearchResult,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    // 构建显示名称：主名称 + 别名
    val displayName = if (!result.aliases.isNullOrBlank()) {
        "${result.name} (${result.aliases})"
    } else {
        result.name
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Text(
                    text = "NORAD ID: ${result.noradId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.whitelist_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 根据转发器数据自动检测卫星类型
 * 主要根据带宽判断：线性转发器通常有较宽的带宽
 */
private suspend fun detectSatelliteTypeByTransmitters(
    dbHelper: DatabaseHelper,
    noradId: String
): EditableWhitelistManager.SatelliteType {
    return try {
        var maxBandwidth = 0L
        var hasWideBandwidthTransmitter = false
        var hasFMMode = false

        dbHelper.getTransmittersByNoradId(noradId.toIntOrNull() ?: 0).collect { transmitters ->
            for (transmitter in transmitters) {
                // 只考虑活跃的转发器
                if (!transmitter.alive) continue

                // 计算带宽
                val bandwidth = calculateBandwidth(transmitter)
                maxBandwidth = maxOf(maxBandwidth, bandwidth)

                // 检测是否有宽带宽转发器（线性特征）
                if (bandwidth > LINEAR_BANDWIDTH_THRESHOLD) {
                    hasWideBandwidthTransmitter = true
                }

                // 检测是否有FM模式（作为辅助判断）
                val mode = transmitter.mode?.uppercase() ?: ""
                val uplinkMode = transmitter.uplinkMode?.uppercase() ?: ""
                if (mode in FM_MODES || uplinkMode in FM_MODES) {
                    hasFMMode = true
                }
            }
        }

        // 主要根据带宽判断
        when {
            // 如果有宽带宽转发器，判定为线性卫星
            hasWideBandwidthTransmitter -> EditableWhitelistManager.SatelliteType.LINEAR
            // 如果最大带宽大于FM阈值但小于线性阈值，根据模式辅助判断
            maxBandwidth > FM_BANDWIDTH_THRESHOLD && maxBandwidth <= LINEAR_BANDWIDTH_THRESHOLD -> {
                // 带宽在中间范围，优先根据是否有FM模式判断
                if (hasFMMode) EditableWhitelistManager.SatelliteType.FM
                else EditableWhitelistManager.SatelliteType.LINEAR
            }
            // 窄带宽，判定为FM卫星
            maxBandwidth > 0 && maxBandwidth <= FM_BANDWIDTH_THRESHOLD -> EditableWhitelistManager.SatelliteType.FM
            // 无法获取带宽信息，默认FM
            else -> EditableWhitelistManager.SatelliteType.FM
        }
    } catch (e: Exception) {
        LogManager.w("WhitelistEditDialog", "检测卫星类型失败: $noradId", e)
        EditableWhitelistManager.SatelliteType.FM // 默认FM
    }
}

// 带宽阈值定义（单位：Hz）
private const val FM_BANDWIDTH_THRESHOLD = 25000L      // FM转发器通常带宽 <= 25kHz
private const val LINEAR_BANDWIDTH_THRESHOLD = 50000L  // 线性转发器通常带宽 > 50kHz

// FM模式列表（辅助判断）
private val FM_MODES = setOf(
    "FM", "FMN", "FMW", "FM_DSTAR", "DMR", "C4FM"
)

/**
 * 计算转发器带宽
 * 返回带宽值（Hz），如果无法计算返回0
 */
private fun calculateBandwidth(transmitter: com.bh6aap.ic705Cter.data.api.Transmitter): Long {
    // 计算下行带宽
    val downlinkBandwidth = if (transmitter.downlinkLow != null && transmitter.downlinkHigh != null) {
        kotlin.math.abs(transmitter.downlinkHigh - transmitter.downlinkLow)
    } else 0L

    // 计算上行带宽
    val uplinkBandwidth = if (transmitter.uplinkLow != null && transmitter.uplinkHigh != null) {
        kotlin.math.abs(transmitter.uplinkHigh - transmitter.uplinkLow)
    } else 0L

    // 返回最大带宽
    return maxOf(downlinkBandwidth, uplinkBandwidth)
}
