package com.bh6aap.ic705Cter.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bh6aap.ic705Cter.data.UserDataManager
import kotlinx.coroutines.launch

/**
 * 数据管理对话框
 * 提供用户数据的导出和导入功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataManagementDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userDataManager = remember { UserDataManager(context) }

    var showImportConfirm by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importMode by remember { mutableStateOf(true) } // true=合并, false=覆盖
    var isProcessing by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }

    // 导出文件选择器
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch {
                isProcessing = true
                val result = userDataManager.exportUserData(it)
                isProcessing = false
                resultMessage = result.getOrElse { "导出失败: ${it.message}" }
                showResult = true
            }
        }
    }

    // 导入文件选择器
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                // 验证文件
                val validationResult = userDataManager.validateExportFile(it)
                if (validationResult.isSuccess) {
                    importUri = it
                    showImportConfirm = true
                } else {
                    resultMessage = "文件验证失败: ${validationResult.exceptionOrNull()?.message}"
                    showResult = true
                }
            }
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
                                    text = "数据管理",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "返回",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 说明文字
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "数据备份与恢复",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "导出功能可以将您的所有自定义数据（呼号、地面站、CW预设、频率预设、自定义转发器、备忘录等）保存到文件中。\n\n导入功能可以从备份文件恢复数据，支持合并模式（保留现有数据）或覆盖模式（替换所有数据）。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // 导出按钮
                    DataActionCard(
                        title = "导出数据",
                        description = "将所有用户数据导出到JSON文件",
                        icon = Icons.Default.KeyboardArrowUp,
                        onClick = {
                            val fileName = UserDataManager.generateExportFileName()
                            exportLauncher.launch(fileName)
                        },
                        enabled = !isProcessing
                    )

                    // 导入按钮
                    DataActionCard(
                        title = "导入数据",
                        description = "从JSON文件导入用户数据",
                        icon = Icons.Default.KeyboardArrowDown,
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        enabled = !isProcessing
                    )

                    if (isProcessing) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    // 导入确认对话框
    if (showImportConfirm && importUri != null) {
        ImportConfirmDialog(
            mergeMode = importMode,
            onMergeModeChange = { importMode = it },
            onConfirm = {
                showImportConfirm = false
                scope.launch {
                    isProcessing = true
                    val result = userDataManager.importUserData(importUri!!, importMode)
                    isProcessing = false
                    resultMessage = result.getOrElse { "导入失败: ${it.message}" }
                    showResult = true
                }
            },
            onDismiss = {
                showImportConfirm = false
                importUri = null
            }
        )
    }

    // 结果对话框
    if (showResult && resultMessage != null) {
        AlertDialog(
            onDismissRequest = {
                showResult = false
                resultMessage = null
            },
            title = {
                Text(
                    text = if (resultMessage!!.contains("失败") || resultMessage!!.contains("错误")) "操作失败" else "操作成功",
                    color = if (resultMessage!!.contains("失败") || resultMessage!!.contains("错误")) 
                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Text(
                    text = resultMessage!!,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResult = false
                    resultMessage = null
                }) {
                    Text("确定")
                }
            }
        )
    }
}

/**
 * 数据操作卡片
 */
@Composable
private fun DataActionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 图标
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // 文字内容
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 导入确认对话框
 */
@Composable
private fun ImportConfirmDialog(
    mergeMode: Boolean,
    onMergeModeChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "导入确认",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "请选择导入模式：",
                    style = MaterialTheme.typography.bodyMedium
                )

                // 合并模式选项
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMergeModeChange(true) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (mergeMode) MaterialTheme.colorScheme.primaryContainer 
                           else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = mergeMode,
                            onClick = { onMergeModeChange(true) }
                        )
                        Column {
                            Text(
                                text = "合并模式",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "保留现有数据，添加新数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 覆盖模式选项
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMergeModeChange(false) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (!mergeMode) MaterialTheme.colorScheme.primaryContainer 
                           else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = !mergeMode,
                            onClick = { onMergeModeChange(false) }
                        )
                        Column {
                            Text(
                                text = "覆盖模式",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "清空现有数据，只保留导入的数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!mergeMode) {
                    Text(
                        text = "⚠️ 警告：覆盖模式将删除所有现有数据！",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("确认导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
