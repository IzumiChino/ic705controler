package com.bh6aap.ic705Cter.ui.state

import com.bh6aap.ic705Cter.data.database.entity.StationEntity
import com.bh6aap.ic705Cter.data.radio.BluetoothSppConnector

/**
 * 主界面UI状态数据类
 * 集中管理主界面的所有状态
 */
data class MainUiState(
    // 地面站数据
    val stationData: StationEntity? = null,
    val maidenheadGrid: String = "",
    
    // 蓝牙连接状态
    val connectionState: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED,
    val isConnecting: Boolean = false,
    val defaultDeviceName: String? = null,
    
    // VFO频率和模式
    val vfoData: VfoData = VfoData(),
    
    // 数据更新时间
    val tleUpdateTime: String = "尚未更新",
    val transmitterUpdateTime: String = "尚未更新",
    
    // 加载和错误状态
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    
    // 对话框显示状态
    val showBluetoothDialog: Boolean = false,
    val showSatelliteDialog: Boolean = false,
    val showSettingsDialog: Boolean = false
) {
    /**
     * 检查是否已连接蓝牙
     */
    fun isBluetoothConnected(): Boolean = connectionState == BluetoothConnectionState.CONNECTED
    
    /**
     * 检查是否有地面站数据
     */
    fun hasStationData(): Boolean = stationData != null
    
    /**
     * 获取连接状态显示文本
     */
    fun getConnectionStatusText(): String = when {
        isConnecting -> "正在连接..."
        isBluetoothConnected() -> "已连接"
        else -> "未连接"
    }
    
    /**
     * 获取VFO A频率显示
     */
    fun getVfoAFrequencyDisplay(): String = vfoData.vfoAFrequency
    
    /**
     * 获取VFO B频率显示
     */
    fun getVfoBFrequencyDisplay(): String = vfoData.vfoBFrequency
}

/**
 * VFO数据类
 * 封装VFO A和VFO B的频率和模式信息
 */
data class VfoData(
    val vfoAFrequency: String = "-",
    val vfoAMode: String = "-",
    val vfoBFrequency: String = "-",
    val vfoBMode: String = "-"
) {
    /**
     * 检查VFO A是否有有效数据
     */
    fun hasVfoAData(): Boolean = vfoAFrequency != "-" && vfoAFrequency.isNotBlank()
    
    /**
     * 检查VFO B是否有有效数据
     */
    fun hasVfoBData(): Boolean = vfoBFrequency != "-" && vfoBFrequency.isNotBlank()
    
    /**
     * 获取VFO A完整显示文本
     */
    fun getVfoADisplay(): String = "$vfoAFrequency ($vfoAMode)"
    
    /**
     * 获取VFO B完整显示文本
     */
    fun getVfoBDisplay(): String = "$vfoBFrequency ($vfoBMode)"
}

/**
 * 蓝牙连接状态枚举
 * 简化版的连接状态，用于UI显示
 */
enum class BluetoothConnectionState {
    DISCONNECTED,   // 未连接
    CONNECTING,     // 连接中
    CONNECTED,      // 已连接
    ERROR           // 连接错误
}

/**
 * 将BluetoothSppConnector的连接状态转换为UI状态
 */
fun BluetoothSppConnector.ConnectionState.toUiState(): BluetoothConnectionState = when (this) {
    BluetoothSppConnector.ConnectionState.DISCONNECTED -> BluetoothConnectionState.DISCONNECTED
    BluetoothSppConnector.ConnectionState.CONNECTING -> BluetoothConnectionState.CONNECTING
    BluetoothSppConnector.ConnectionState.CONNECTED -> BluetoothConnectionState.CONNECTED
    BluetoothSppConnector.ConnectionState.ERROR -> BluetoothConnectionState.ERROR
}

/**
 * 主界面事件密封类
 * 用于处理用户交互事件
 */
sealed class MainEvent {
    // 蓝牙相关事件
    object OnBluetoothCardClick : MainEvent()
    object OnBluetoothCardLongClick : MainEvent()
    object OnBluetoothDialogDismiss : MainEvent()
    data class OnBluetoothDeviceSelected(val deviceAddress: String) : MainEvent()
    
    // GPS相关事件
    object OnGpsRefreshClick : MainEvent()
    
    // 设置相关事件
    object OnSettingsClick : MainEvent()
    object OnSettingsDialogDismiss : MainEvent()
    
    // 数据更新事件
    object OnTleUpdateClick : MainEvent()
    object OnTransmitterUpdateClick : MainEvent()
    
    // 错误处理事件
    object OnErrorDismiss : MainEvent()
}

/**
 * 主界面副作用密封类
 * 用于处理一次性事件，如Toast、导航等
 */
sealed class MainEffect {
    data class ShowToast(val message: String) : MainEffect()
    data class NavigateToSatelliteTracking(val satelliteId: String?) : MainEffect()
    object RequestBluetoothPermission : MainEffect()
    object RequestLocationPermission : MainEffect()
}
