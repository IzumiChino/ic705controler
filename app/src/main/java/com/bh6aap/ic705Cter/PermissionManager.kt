package com.bh6aap.ic705Cter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * 权限管理类
 * 统一管理应用所需的所有权限请求
 */
class PermissionManager(private val activity: ComponentActivity) {

    private var permissionCallback: ((PermissionResult) -> Unit)? = null

    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = mutableListOf<String>()
            val denied = mutableListOf<String>()

            permissions.forEach { (permission, isGranted) ->
                if (isGranted) {
                    granted.add(permission)
                } else {
                    denied.add(permission)
                }
            }

            val result = PermissionResult(
                allGranted = denied.isEmpty(),
                grantedPermissions = granted,
                deniedPermissions = denied
            )
            permissionCallback?.invoke(result)
        }

    /**
     * 获取应用所需的所有权限列表
     */
    fun getRequiredPermissions(): List<PermissionItem> {
        return listOf(
            PermissionItem(
                Manifest.permission.ACCESS_FINE_LOCATION,
                "位置权限",
                "获取当前地面站精确位置",
                true
            ),
            PermissionItem(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                "位置权限（粗略）",
                "获取当前地面站大致位置",
                true
            ),
            PermissionItem(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                "存储读取权限",
                "读取卫星数据文件",
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
            ),
            PermissionItem(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                "存储写入权限",
                "缓存卫星数据",
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
            ),
            PermissionItem(
                Manifest.permission.BLUETOOTH_SCAN,
                "蓝牙扫描权限",
                "扫描 IC-705 设备",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ),
            PermissionItem(
                Manifest.permission.BLUETOOTH_CONNECT,
                "蓝牙连接权限",
                "连接 IC-705 设备",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ),
            PermissionItem(
                Manifest.permission.BLUETOOTH,
                "蓝牙权限",
                "连接 IC-705 设备",
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            ),
            PermissionItem(
                Manifest.permission.BLUETOOTH_ADMIN,
                "蓝牙管理权限",
                "管理蓝牙连接",
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            ),
            PermissionItem(
                Manifest.permission.RECORD_AUDIO,
                "麦克风权限",
                "CW解码功能需要录制音频",
                true
            ),
            PermissionItem(
                Manifest.permission.POST_NOTIFICATIONS,
                "通知权限",
                "卫星过境提醒需要发送通知",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            )
        ).filter { it.required }
    }

    /**
     * 检查是否已授予所有必要权限
     */
    fun hasAllPermissions(): Boolean {
        return getRequiredPermissions().all { permissionItem ->
            ContextCompat.checkSelfPermission(activity, permissionItem.permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 获取需要请求的权限列表
     */
    fun getPermissionsToRequest(): List<String> {
        return getRequiredPermissions()
            .filter { permissionItem ->
                ContextCompat.checkSelfPermission(activity, permissionItem.permission) !=
                        PackageManager.PERMISSION_GRANTED
            }
            .map { it.permission }
    }

    /**
     * 请求权限
     * @param callback 权限请求结果回调
     */
    fun requestPermissions(callback: (PermissionResult) -> Unit) {
        val permissionsToRequest = getPermissionsToRequest()

        if (permissionsToRequest.isEmpty()) {
            // 所有权限已授予
            callback(
                PermissionResult(
                    allGranted = true,
                    grantedPermissions = getRequiredPermissions().map { it.permission },
                    deniedPermissions = emptyList()
                )
            )
        } else {
            permissionCallback = callback
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    /**
     * 获取权限的友好名称
     */
    fun getPermissionFriendlyName(permission: String): String {
        return getRequiredPermissions().find { it.permission == permission }?.name
            ?: permission.substringAfterLast(".")
    }

    /**
     * 获取权限的描述
     */
    fun getPermissionDescription(permission: String): String {
        return getRequiredPermissions().find { it.permission == permission }?.description
            ?: ""
    }

    /**
     * 获取权限说明文本
     */
    fun getPermissionRationaleText(): String {
        val permissions = getRequiredPermissions()
        val grouped = permissions.groupBy {
            when {
                it.permission.contains("LOCATION") -> "位置"
                it.permission.contains("STORAGE") || it.permission.contains("EXTERNAL") -> "存储"
                it.permission.contains("BLUETOOTH") -> "蓝牙"
                it.permission.contains("NOTIFICATION") -> "通知"
                else -> "其他"
            }
        }

        val sb = StringBuilder("需要以下权限才能正常运行：\n\n")
        grouped.forEach { (category, items) ->
            val description = when (category) {
                "位置" -> "获取当前地面站位置"
                "存储" -> "缓存卫星数据"
                "蓝牙" -> "连接 IC-705 设备"
                "通知" -> "卫星过境提醒通知"
                else -> ""
            }
            sb.append("• ").append(category).append("权限 - ").append(description).append("\n")
        }
        return sb.toString()
    }
}

/**
 * 权限项数据类
 */
data class PermissionItem(
    val permission: String,
    val name: String,
    val description: String,
    val required: Boolean = true
)

/**
 * 权限请求结果
 */
data class PermissionResult(
    val allGranted: Boolean,
    val grantedPermissions: List<String>,
    val deniedPermissions: List<String>
)
