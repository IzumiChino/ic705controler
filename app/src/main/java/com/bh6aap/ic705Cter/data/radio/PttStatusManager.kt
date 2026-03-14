package com.bh6aap.ic705Cter.data.radio

import com.bh6aap.ic705Cter.util.LogManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * PTT状态管理器
 * 用于监听模式状态广播并查询PTT状态
 * 当收到模式状态广播时，自动发送PTT状态查询命令
 */
class PttStatusManager private constructor(
    private val bluetoothConnectionManager: BluetoothConnectionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isTracking = false
    private var lastQueryTime = 0L
    private val queryIntervalMs = 500L // 最小查询间隔500ms，避免过于频繁

    // 模式设置/查询命令 (参考CIV协议手册)
    private val CMD_SET_MODE_VFO_A: Byte = 0x01    // 设置/查询 VFO A 模式 (格式: 01 XX YY，XX=模式, YY=滤波器)
    private val CMD_SET_MODE_VFO_B: Byte = 0x04    // 设置/查询 VFO B 模式 (格式: 04 XX YY，XX=模式, YY=滤波器)

    // PTT状态查询命令
    private val CMD_READ_STATUS: Byte = 0x1C       // 读取状态命令
    private val SUBCMD_PTT_STATUS: Byte = 0x00     // PTT状态子命令

    // 地址
    private val CONTROLLER_ADDRESS: Byte = 0x00     // 控制器地址
    private val IC705_ADDRESS: Byte = 0xA4.toByte() // IC-705地址

    // PTT状态回调
    private val pttStatusListeners = ConcurrentHashMap<String, (Boolean) -> Unit>()

    companion object {
        private const val TAG = "PttStatusManager"

        @Volatile
        private var instance: PttStatusManager? = null

        fun getInstance(bluetoothConnectionManager: BluetoothConnectionManager): PttStatusManager {
            return instance ?: synchronized(this) {
                instance ?: PttStatusManager(bluetoothConnectionManager).also {
                    instance = it
                }
            }
        }
    }

    /**
     * 开始监听（在卫星跟踪开启时调用）
     */
    fun startListening() {
        if (isTracking) {
            LogManager.w(TAG, "PTT状态监听已经在运行中")
            return
        }
        isTracking = true
        LogManager.i(TAG, "开始PTT状态监听")
    }

    /**
     * 停止监听（在卫星跟踪停止时调用）
     */
    fun stopListening() {
        isTracking = false
        LogManager.i(TAG, "停止PTT状态监听")
    }

    /**
     * 处理从电台接收到的响应
     * 当检测到模式设置命令时，发送PTT状态查询
     * @param response CIV响应数据
     */
    fun processResponse(response: ByteArray) {
        if (!isTracking || response.size < 6) return

        // 检查是否是模式设置命令（从控制器发送到电台）
        // 格式: FE FE 00 A4 01 XX YY FD (设置VFO A模式)
        // 格式: FE FE 00 A4 04 XX YY FD (设置VFO B模式)
        if (isModeSetCommand(response)) {
            val vfo = if (response[4] == CMD_SET_MODE_VFO_A) "VFO A" else "VFO B"
            val mode = response[5]
            val filter = response[6]
            val modeName = getModeName(mode)
            val filterName = getFilterName(filter)
            LogManager.d(TAG, "检测到模式设置命令: $vfo 模式=$modeName($mode), 滤波器=$filterName($filter)，准备查询PTT状态")
            queryPttStatus()
        }

        // 检查是否是PTT状态响应
        // 格式: FE FE A4 00 1C 00 ** FD
        if (isPttStatusResponse(response)) {
            parsePttStatus(response)
        }
    }

    /**
     * 获取模式名称
     */
    private fun getModeName(mode: Byte): String {
        return when (mode.toInt()) {
            0x00 -> "LSB"
            0x01 -> "USB"
            0x02 -> "AM"
            0x03 -> "CW"
            0x04 -> "RTTY"
            0x05 -> "FM"
            0x06 -> "WFM"
            0x07 -> "CW-R"
            0x08 -> "RTTY-R"
            0x17 -> "DV"
            else -> "未知"
        }
    }

    /**
     * 获取滤波器名称
     */
    private fun getFilterName(filter: Byte): String {
        return when (filter.toInt()) {
            0x01 -> "FIL1"
            0x02 -> "FIL2"
            0x03 -> "FIL3"
            else -> "默认"
        }
    }

    /**
     * 检查是否是模式设置/查询命令（从控制器发送到电台）
     * 格式: FE FE 00 A4 01 XX YY FD (设置VFO A模式，XX=模式, YY=滤波器)
     * 格式: FE FE 00 A4 04 XX YY FD (设置VFO B模式，XX=模式, YY=滤波器)
     * 例如: FE FE 00 A4 01 01 01 FD = VFO A 设置为USB模式，滤波器FIL1
     */
    private fun isModeSetCommand(response: ByteArray): Boolean {
        // 最小长度检查：帧头(2) + 地址(2) + 命令(1) + 模式(1) + 滤波器(1) + 结束符(1) = 8字节
        if (response.size < 8) return false

        // 检查帧头 FE FE
        if (response[0] != 0xFE.toByte() || response[1] != 0xFE.toByte()) return false

        // 检查源地址（控制器00）和目标地址（IC-705 A4）
        if (response[2] != CONTROLLER_ADDRESS || response[3] != IC705_ADDRESS) return false

        // 检查命令字节 (01=设置VFO A模式, 04=设置VFO B模式)
        val command = response[4]
        if (command != CMD_SET_MODE_VFO_A && command != CMD_SET_MODE_VFO_B) return false

        // 检查模式字节是否有效 (00=LSB, 01=USB, 02=AM, 03=CW, 04=RTTY, 05=FM, 06=WFM, 07=CW-R, 08=RTTY-R, 17=DV)
        val mode = response[5]
        val validModes = listOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x17)
        if (mode.toInt() !in validModes) return false

        // 检查结束符 FD
        if (response[response.size - 1] != 0xFD.toByte()) return false

        return true
    }

    /**
     * 检查是否是PTT状态响应
     * 格式: FE FE A4 00 1C 00 ** FD
     * 其中 ** = 00 (RX接收) 或 01 (TX发射)
     */
    private fun isPttStatusResponse(response: ByteArray): Boolean {
        // 最小长度检查：帧头(2) + 地址(2) + 命令(1) + 子命令(1) + PTT状态(1) + 结束符(1) = 8字节
        if (response.size < 8) return false

        // 检查帧头 FE FE
        if (response[0] != 0xFE.toByte() || response[1] != 0xFE.toByte()) return false

        // 检查源地址（IC-705 A4）和目标地址（控制器00）
        if (response[2] != IC705_ADDRESS || response[3] != CONTROLLER_ADDRESS) return false

        // 检查命令 (1C=读取状态)
        if (response[4] != CMD_READ_STATUS) return false

        // 检查子命令 (00=PTT状态)
        if (response[5] != SUBCMD_PTT_STATUS) return false

        // 检查PTT状态字节 (00=RX, 01=TX)
        val pttStatus = response[6]
        if (pttStatus != 0x00.toByte() && pttStatus != 0x01.toByte()) return false

        // 检查结束符 FD
        if (response[response.size - 1] != 0xFD.toByte()) return false

        return true
    }

    /**
     * 发送PTT状态查询命令
     * 命令: FE FE A4 00 1C 00 FD
     */
    private fun queryPttStatus() {
        val now = System.currentTimeMillis()
        if (now - lastQueryTime < queryIntervalMs) {
            // 避免过于频繁查询
            return
        }
        lastQueryTime = now

        scope.launch {
            try {
                val civController = bluetoothConnectionManager.civController.value
                if (civController == null) {
                    LogManager.w(TAG, "CIV控制器未初始化，无法查询PTT状态")
                    return@launch
                }

                // 构建PTT状态查询命令
                val command = byteArrayOf(
                    0xFE.toByte(), 0xFE.toByte(),  // 帧头
                    IC705_ADDRESS,                 // 目标地址（IC-705）
                    CONTROLLER_ADDRESS,            // 源地址（控制器）
                    CMD_READ_STATUS,               // 命令：读取状态
                    SUBCMD_PTT_STATUS              // 子命令：PTT状态
                )

                // 发送命令
                val result = civController.sendRawCommand(command)
                LogManager.d(TAG, "发送PTT状态查询命令，结果: $result")
            } catch (e: Exception) {
                LogManager.e(TAG, "发送PTT状态查询命令失败", e)
            }
        }
    }

    /**
     * 解析PTT状态响应
     * 响应格式: FE FE A4 00 1C 00 ** FD
     * 其中 ** = 00 (RX接收) 或 01 (TX发射)
     */
    private fun parsePttStatus(response: ByteArray) {
        if (response.size < 7) {
            LogManager.w(TAG, "PTT状态响应数据太短: ${response.size}字节")
            return
        }

        // 获取PTT状态字节
        val pttStatus = response[6]
        val isTransmitting = pttStatus == 0x01.toByte()

        LogManager.i(TAG, "PTT状态: ${if (isTransmitting) "发射中(TX)" else "接收(RX)"}")

        // 通知所有监听器
        notifyPttStatusListeners(isTransmitting)
    }

    /**
     * 添加PTT状态监听器
     * @param key 监听器标识
     * @param listener 回调函数，参数为是否正在发射
     */
    fun addPttStatusListener(key: String, listener: (Boolean) -> Unit) {
        pttStatusListeners[key] = listener
        LogManager.d(TAG, "添加PTT状态监听器: $key")
    }

    /**
     * 移除PTT状态监听器
     * @param key 监听器标识
     */
    fun removePttStatusListener(key: String) {
        pttStatusListeners.remove(key)
        LogManager.d(TAG, "移除PTT状态监听器: $key")
    }

    /**
     * 通知所有监听器PTT状态变化
     */
    private fun notifyPttStatusListeners(isTransmitting: Boolean) {
        pttStatusListeners.forEach { (key, listener) ->
            try {
                listener(isTransmitting)
            } catch (e: Exception) {
                LogManager.e(TAG, "通知PTT状态监听器失败: $key", e)
            }
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        stopListening()
        pttStatusListeners.clear()
        scope.cancel()
        LogManager.i(TAG, "PTT状态管理器已清理")
    }
}
