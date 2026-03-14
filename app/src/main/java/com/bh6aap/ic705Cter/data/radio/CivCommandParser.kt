package com.bh6aap.ic705Cter.data.radio

import com.bh6aap.ic705Cter.util.LogManager

/**
 * CIV指令解析器
 * 解析CIV指令响应
 */
class CivCommandParser {
    
    companion object {
        private const val TAG = "CivCommandParser"
        private const val FIXED_IF_FREQUENCY = 38.85 // 中频频率（MHz）
    }
    
    /**
     * 指令解析结果
     */
    sealed class CivCommandResult {
        data class FrequencyResult(val localOscillatorFreqMHz: Double, val actualFreqMHz: Double) : CivCommandResult()
        data class ModeResult(val mode: Byte, val bandwidth: Byte) : CivCommandResult()
        data class VfoStatusResult(val currentVfo: Byte) : CivCommandResult()
    }
    
    /**
     * 解析CIV指令
     * @param command CIV指令字节数组
     * @return 解析结果，失败返回null
     */
    fun parseCommand(command: ByteArray): CivCommandResult? {
        LogManager.i(TAG, "【CIV解析】开始解析CIV指令")
        
        if (command.size < 7) {
            LogManager.e(TAG, "【CIV解析】指令长度不足")
            return null
        }
        
        val targetAddr = command[2]
        val sourceAddr = command[3]
        val commandCode = command[4]
        
        LogManager.d(TAG, "【CIV解析】目标地址: 0x${String.format("%02X", targetAddr)}")
        LogManager.d(TAG, "【CIV解析】源地址: 0x${String.format("%02X", sourceAddr)}")
        LogManager.d(TAG, "【CIV解析】指令代码: 0x${String.format("%02X", commandCode)}")
        
        val dataStartIndex = 5
        val dataEndIndex = command.size - 1 // 排除结束字节FD
        
        return when (commandCode) {
            0x00.toByte(), 0x03.toByte() -> parseFrequencyCommand(command, dataStartIndex, dataEndIndex)
            0x01.toByte(), 0x04.toByte() -> parseModeCommand(command, dataStartIndex, dataEndIndex)
            0x26.toByte() -> parseVfoStatusCommand(command, dataStartIndex, dataEndIndex)
            else -> {
                LogManager.i(TAG, "【CIV解析】收到未处理的指令，代码: 0x${String.format("%02X", commandCode)}")
                null
            }
        }
    }
    
    /**
     * 解析频率指令
     * @param command CIV指令字节数组
     * @param dataStartIndex 数据起始索引
     * @param dataEndIndex 数据结束索引
     * @return 频率解析结果
     */
    private fun parseFrequencyCommand(
        command: ByteArray,
        dataStartIndex: Int,
        dataEndIndex: Int
    ): CivCommandResult {
        LogManager.i(TAG, "【CIV解析】解析频率指令")
        
        if (dataStartIndex >= dataEndIndex) {
            LogManager.e(TAG, "【CIV解析】频率数据长度不足")
            return CivCommandResult.FrequencyResult(0.0, 0.0)
        }
        
        // 提取频率数据（5字节BCD码）
        val freqData = ByteArray(5)
        val commandCode = command[4]
        
        if (commandCode == 0x25.toByte()) {
            // 对于0x25命令，频率数据从VFO标识后开始
            if (dataStartIndex + 1 <= dataEndIndex - 5) {
                System.arraycopy(command, dataStartIndex + 1, freqData, 0, 5)
                LogManager.d(TAG, "【CIV解析】0x25命令频率数据: ${bytesToHex(freqData)}")
            } else {
                LogManager.e(TAG, "【CIV解析】0x25命令频率数据长度不足")
                return CivCommandResult.FrequencyResult(0.0, 0.0)
            }
        } else {
            // 对于其他命令，频率数据直接从dataStartIndex开始
            System.arraycopy(command, dataStartIndex, freqData, 0, 5)
            LogManager.d(TAG, "【CIV解析】其他命令频率数据: ${bytesToHex(freqData)}")
        }
        
        // 解码频率（单位Hz）
        val decodedFreq = decodeFrequency(freqData)
        LogManager.d(TAG, "【CIV解析】解码频率: $decodedFreq Hz")
        
        // 转换为MHz
        val decodedFreqMHz = decodedFreq / 1000000.0
        LogManager.d(TAG, "【CIV解析】解码频率: $decodedFreqMHz MHz")
        
        // 确定实际频率
        val actualFreq: Double
        
        // 参考项目的频率转换逻辑：
        // 0.03MHz～24.999MHz：直接采样，不需要转换
        // 25MHz及以上：使用38.85MHz中频，需要转换
        // 注意：当读取到的频率小于25MHz但实际应该是V/U段时，需要加上38.85MHz中频
        if (decodedFreqMHz < 25.0) {
            // 如果频率小于25MHz，可能是中频偏移值，需要加上中频得到实际频率
            actualFreq = decodedFreqMHz + FIXED_IF_FREQUENCY
            LogManager.d(TAG, "【CIV解析】中频计算: $decodedFreqMHz + $FIXED_IF_FREQUENCY = $actualFreq")
        } else {
            // 对于其他情况，直接使用解码后的频率
            actualFreq = decodedFreqMHz
            LogManager.d(TAG, "【CIV解析】直接使用频率: $actualFreq MHz")
        }
        
        LogManager.i(TAG, "【CIV解析】实际射频频率: $actualFreq MHz")
        
        return CivCommandResult.FrequencyResult(decodedFreqMHz, actualFreq)
    }
    
    /**
     * 解析模式指令
     * @param command CIV指令字节数组
     * @param dataStartIndex 数据起始索引
     * @param dataEndIndex 数据结束索引
     * @return 模式解析结果
     */
    private fun parseModeCommand(
        command: ByteArray,
        dataStartIndex: Int,
        dataEndIndex: Int
    ): CivCommandResult {
        LogManager.i(TAG, "【CIV解析】解析模式指令")
        
        if (dataStartIndex >= dataEndIndex) {
            LogManager.e(TAG, "【CIV解析】模式数据长度不足")
            return CivCommandResult.ModeResult(0, 0)
        }
        
        val commandCode = command[4]
        var modeIndex = dataStartIndex
        
        // 对于带VFO选择的0x04命令，数据格式是：VFO标识(1字节) + 模式码(1字节) + 带宽(1字节)
        if (commandCode == 0x04.toByte() && dataEndIndex - dataStartIndex >= 3) {
            // 检查是否有VFO标识（0x00或0x01）
            val possibleVfo = command[dataStartIndex]
            if (possibleVfo == 0x00.toByte() || possibleVfo == 0x01.toByte()) {
                // 跳过VFO标识
                modeIndex = dataStartIndex + 1
                LogManager.d(TAG, "【CIV解析】检测到VFO标识: 0x${String.format("%02X", possibleVfo)}")
            }
        }
        
        // 提取模式数据
        val mode = command[modeIndex]
        val bandwidth = if (dataEndIndex > modeIndex + 1) command[modeIndex + 1] else 0
        
        LogManager.d(TAG, "【CIV解析】模式: 0x${String.format("%02X", mode)}, 带宽: 0x${String.format("%02X", bandwidth)}")
        
        return CivCommandResult.ModeResult(mode, bandwidth)
    }
    
    /**
     * 解析VFO状态指令
     * @param command CIV指令字节数组
     * @param dataStartIndex 数据起始索引
     * @param dataEndIndex 数据结束索引
     * @return VFO状态解析结果
     */
    private fun parseVfoStatusCommand(
        command: ByteArray,
        dataStartIndex: Int,
        dataEndIndex: Int
    ): CivCommandResult {
        LogManager.i(TAG, "【CIV解析】解析VFO状态指令")
        
        if (dataStartIndex >= dataEndIndex) {
            LogManager.e(TAG, "【CIV解析】VFO状态数据长度不足")
            return CivCommandResult.VfoStatusResult(0)
        }
        
        // 提取VFO状态数据
        val vfoData = command.sliceArray(dataStartIndex until dataEndIndex)
        LogManager.d(TAG, "【CIV解析】VFO状态数据: ${bytesToHex(vfoData)}")
        
        // 根据数据格式解析当前VFO
        // 0x26指令响应格式：00 XX 00 01，其中XX表示当前VFO
        // 根据实际测试：00=B信道，01=A信道
        val currentVfo = if (vfoData.size >= 4) {
            vfoData[1] // 第2个字节表示当前VFO
        } else if (vfoData.isNotEmpty()) {
            vfoData[0]
        } else {
            0
        }
        
        LogManager.d(TAG, "【CIV解析】当前VFO: 0x${String.format("%02X", currentVfo)}")
        
        return CivCommandResult.VfoStatusResult(currentVfo)
    }
    
    /**
     * 解码频率数据
     * @param data 5字节BCD码
     * @return 频率（Hz）
     *
     * IC-705频率数据已经是Hz为单位
     * 例如：BCD数据 [0x00, 0x50, 0x45, 0x01, 0x00] 表示 145500000 Hz = 145.500 MHz
     */
    private fun decodeFrequency(data: ByteArray): Long {
        var frequency: Long = 0
        
        // 反转字节顺序，从最后一个字节开始解码
        // 因为IC-705的频率数据是低位在前，高位在后
        for (i in data.size - 1 downTo 0) {
            val byte = data[i].toInt() and 0xFF
            val highNibble = (byte shr 4).toLong()
            val lowNibble = (byte and 0x0F).toLong()
            
            frequency = frequency * 10 + highNibble
            frequency = frequency * 10 + lowNibble
        }
        
        return frequency
    }
    
    /**
     * 获取频段类型
     * @param frequencyMHz 频率（MHz）
     * @return 频段类型
     */
    fun getBandType(frequencyMHz: Double): String {
        return when {
            frequencyMHz < 30 -> "HF"
            frequencyMHz < 1000 -> "VHF/UHF"
            else -> "SHF"
        }
    }
    
    /**
     * 判断是否是V/U段
     * @param frequencyMHz 频率（MHz）
     * @return 是否是V/U段
     */
    fun isVUSegment(frequencyMHz: Double): Boolean {
        return frequencyMHz >= 30 && frequencyMHz < 1000
    }
    
    /**
     * 将字节数组转换为十六进制字符串
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
