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
import com.bh6aap.ic705Cter.R
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

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
                resultMessage = result.getOrElse { context.getString(R.string.data_export) + context.getString(R.string.common_failed) + ": ${it.message}" }
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
                    resultMessage = context.getString(R.string.data_import_validation_failed, validationResult.exceptionOrNull()?.message ?: "")
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
                                    text = stringResource(R.string.data_management_title),
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
                                text = stringResource(R.string.data_backup_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(R.string.data_backup_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // 导出按钮
                    DataActionCard(
                        title = stringResource(R.string.data_export),
                        description = stringResource(R.string.data_export_desc),
                        icon = Icons.Default.KeyboardArrowUp,
                        onClick = {
                            val fileName = UserDataManager.generateExportFileName()
                            exportLauncher.launch(fileName)
                        },
                        enabled = !isProcessing
                    )

                    // 导入按钮
                    DataActionCard(
                        title = stringResource(R.string.data_import),
                        description = stringResource(R.string.data_import_desc),
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
                    resultMessage = result.getOrElse { context.getString(R.string.data_import) + context.getString(R.string.common_failed) + ": ${it.message}" }
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
        val isError = resultMessage!!.contains(context.getString(R.string.common_failed)) || resultMessage!!.contains(context.getString(R.string.common_error))
        AlertDialog(
            onDismissRequest = {
                showResult = false
                resultMessage = null
            },
            title = {
                Text(
                    text = if (isError) stringResource(R.string.data_operation_failed) else stringResource(R.string.data_operation_success),
                    color = if (isError)
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
                    Text(stringResource(R.string.common_ok))
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
                text = stringResource(R.string.data_import_confirm_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.data_import_select_mode),
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
                                text = stringResource(R.string.data_import_merge_mode),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.data_import_merge_desc),
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
                                text = stringResource(R.string.data_import_overwrite_mode),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.data_import_overwrite_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!mergeMode) {
                    Text(
                        text = stringResource(R.string.data_import_overwrite_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.data_import_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
