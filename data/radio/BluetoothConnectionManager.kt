package com.example.ic705controler.data.radio

import android.bluetooth.BluetoothDevice
import com.example.ic705controler.util.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 蓝牙连接管理器
 * 管理与ICOM电台的蓝牙连接状态
 */
class BluetoothConnectionManager {
    
    companion object {
        private const val TAG = "BluetoothConnectionManager"
    }
    
    // 状态管理
    private val _currentConnector = MutableStateFlow<BluetoothSppConnector?>(null)
    val currentConnector: StateFlow<BluetoothSppConnector?> = _currentConnector.asStateFlow()
    
    private val _connectionState = MutableStateFlow<BluetoothSppConnector.ConnectionState>(
        BluetoothSppConnector.ConnectionState.DISCONNECTED
    )
    val connectionState: StateFlow<BluetoothSppConnector.ConnectionState> = _connectionState.asStateFlow()
    
    private val _civManager = MutableStateFlow<CivCommandManager?>(null)
    val civManager: StateFlow<CivCommandManager?> = _civManager.asStateFlow()
    
    /**
     * 连接到蓝牙设备
     * @param device 蓝牙设备
     * @return 是否开始连接
     */
    suspend fun connectToDevice(device: BluetoothDevice): Boolean {
        LogManager.i(TAG, "【蓝牙连接】开始连接到设备: ${device.name} (${device.address})")
        
        // 断开现有连接
        disconnect()
        
        try {
            // 创建新的SPP连接器
            val connector = BluetoothSppConnector(device)
            
            // 设置回调
            connector.setCallback(object : BluetoothSppConnector.Callback {
                override fun onDataReceived(data: ByteArray) {
                    LogManager.d(TAG, "【蓝牙连接】收到数据: ${bytesToHex(data)}")
                }
                
                override fun onCivCommandReceived(command: ByteArray) {
                    LogManager.i(TAG, "【蓝牙连接】收到CIV指令")
                    _civManager.value?.processResponse(command)
                }
                
                override fun onConnectionStateChanged(state: BluetoothSppConnector.ConnectionState) {
                    LogManager.i(TAG, "【蓝牙连接】连接状态变化: $state")
                    _connectionState.value = state
                }
            })
            
            // 创建CIV指令管理器
            val civManager = CivCommandManager(connector)
            _civManager.value = civManager
            
            // 连接到设备
            val success = connector.connect()
            
            if (success) {
                LogManager.i(TAG, "【蓝牙连接】连接成功")
                _currentConnector.value = connector
            } else {
                LogManager.e(TAG, "【蓝牙连接】连接失败")
                _civManager.value = null
            }
            
            return success
        } catch (e: Exception) {
            LogManager.e(TAG, "【蓝牙连接】连接异常", e)
            _civManager.value = null
            return false
        }
    }
    
    /**
     * 断开与当前设备的连接
     */
    fun disconnect() {
        LogManager.i(TAG, "【蓝牙连接】断开连接")
        
        try {
            _currentConnector.value?.disconnect()
            _currentConnector.value = null
            _civManager.value = null
            _connectionState.value = BluetoothSppConnector.ConnectionState.DISCONNECTED
            LogManager.i(TAG, "【蓝牙连接】断开成功")
        } catch (e: Exception) {
            LogManager.e(TAG, "【蓝牙连接】断开连接失败", e)
        }
    }
    
    /**
     * 发送CIV指令
     * @param commandCode 指令代码
     * @param data 数据（可选）
     * @return 是否发送成功
     */
    suspend fun sendCivCommand(commandCode: Byte, data: ByteArray = ByteArray(0)): Boolean {
        return _civManager.value?.sendCivCommand(commandCode, data) ?: false
    }
    
    /**
     * 读取当前频率
     * @return 是否读取成功
     */
    suspend fun readFrequency(): Boolean {
        return _civManager.value?.readFrequency() ?: false
    }
    
    /**
     * 读取当前模式
     * @return 是否读取成功
     */
    suspend fun readMode(): Boolean {
        return _civManager.value?.readMode() ?: false
    }
    
    /**
     * 检查是否已连接
     * @return 是否已连接
     */
    fun isConnected(): Boolean {
        return _currentConnector.value?.isConnected() ?: false
    }
    
    /**
     * 获取当前连接状态
     * @return 连接状态
     */
    fun getConnectionState(): BluetoothSppConnector.ConnectionState {
        return _connectionState.value
    }
    
    /**
     * 将字节数组转换为十六进制字符串
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }
}
