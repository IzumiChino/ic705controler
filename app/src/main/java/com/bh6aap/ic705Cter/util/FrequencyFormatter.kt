package com.bh6aap.ic705Cter.util

/**
 * 频率格式化工具类
 * 统一处理所有频率相关的格式化操作
 */
object FrequencyFormatter {

    /**
     * 格式化频率显示
     * @param frequencyHz 频率值（赫兹）
     * @param precision 小数精度，默认为3位（MHz单位时强制使用6位）
     * @return 格式化后的字符串，如 "435.500000 MHz"
     */
    @JvmOverloads
    fun format(frequencyHz: Long, precision: Int = 3): String = when {
        frequencyHz >= 1_000_000_000 -> formatGhz(frequencyHz, precision)
        frequencyHz >= 1_000_000 -> formatMhz(frequencyHz, 6) // MHz单位强制6位小数
        frequencyHz >= 1_000 -> formatKhz(frequencyHz, precision)
        else -> "$frequencyHz Hz"
    }

    /**
     * 格式化Double类型的频率
     * @param frequencyHz 频率值（赫兹）
     * @param precision 小数精度，默认为3位（MHz单位时强制使用6位）
     * @return 格式化后的字符串
     */
    @JvmOverloads
    fun format(frequencyHz: Double, precision: Int = 3): String = format(frequencyHz.toLong(), precision)

    /**
     * 格式化频率范围
     * @param lowHz 最低频率（赫兹）
     * @param highHz 最高频率（赫兹）
     * @param precision 小数精度（MHz单位时强制使用6位）
     * @return 格式化后的范围字符串，如 "435.500000 - 435.600000 MHz"
     */
    @JvmOverloads
    fun formatRange(lowHz: Long?, highHz: Long?, precision: Int = 3): String {
        if (lowHz == null) return "N/A"
        // MHz范围使用6位小数
        val actualPrecision = if (lowHz >= 1_000_000 && (highHz == null || highHz >= 1_000_000)) 6 else precision

        return if (highHz != null && highHz != lowHz) {
            "${format(lowHz, actualPrecision)} - ${format(highHz, actualPrecision)}"
        } else {
            format(lowHz, actualPrecision)
        }
    }

    /**
     * 格式化带多普勒补偿的频率
     * @param frequencyHz 原始频率（赫兹）
     * @param dopplerShiftHz 多普勒频移（赫兹）
     * @param precision 小数精度（MHz单位时强制使用6位）
     * @return 补偿后的频率字符串
     */
    @JvmOverloads
    fun formatWithDoppler(frequencyHz: Long, dopplerShiftHz: Double, precision: Int = 3): String {
        val compensatedFreq = frequencyHz + dopplerShiftHz
        // MHz频率使用6位小数
        val actualPrecision = if (compensatedFreq >= 1_000_000) 6 else precision
        return format(compensatedFreq.toLong(), actualPrecision)
    }

    /**
     * 格式化GHz单位
     */
    private fun formatGhz(frequencyHz: Long, precision: Int): String {
        val format = "%.${precision}f GHz"
        return String.format(format, frequencyHz / 1_000_000_000.0)
    }

    /**
     * 格式化MHz单位
     */
    private fun formatMhz(frequencyHz: Long, precision: Int): String {
        val format = "%.${precision}f MHz"
        return String.format(format, frequencyHz / 1_000_000.0)
    }

    /**
     * 格式化kHz单位
     */
    private fun formatKhz(frequencyHz: Long, precision: Int): String {
        val format = "%.${precision}f kHz"
        return String.format(format, frequencyHz / 1_000.0)
    }

    /**
     * 解析频率字符串为赫兹值
     * @param frequencyStr 频率字符串，如 "435.5 MHz"
     * @return 赫兹值，解析失败返回null
     */
    fun parse(frequencyStr: String): Long? {
        val trimmed = frequencyStr.trim()
        
        return try {
            when {
                trimmed.contains("GHz", ignoreCase = true) -> {
                    val value = trimmed.replace(Regex("[^0-9.]"), "").toDouble()
                    (value * 1_000_000_000).toLong()
                }
                trimmed.contains("MHz", ignoreCase = true) -> {
                    val value = trimmed.replace(Regex("[^0-9.]"), "").toDouble()
                    (value * 1_000_000).toLong()
                }
                trimmed.contains("kHz", ignoreCase = true) -> {
                    val value = trimmed.replace(Regex("[^0-9.]"), "").toDouble()
                    (value * 1_000).toLong()
                }
                trimmed.contains("Hz", ignoreCase = true) -> {
                    trimmed.replace(Regex("[^0-9.]"), "").toLong()
                }
                else -> trimmed.toDoubleOrNull()?.toLong()
            }
        } catch (e: Exception) {
            LogManager.e("FrequencyFormatter", "解析频率失败: $frequencyStr", e)
            null
        }
    }

    /**
     * 格式化频率差值（用于显示多普勒频移）
     * @param deltaHz 频率差值（赫兹）
     * @return 格式化后的字符串，如 "+1.250 kHz" 或 "-500 Hz"
     */
    fun formatDelta(deltaHz: Double): String {
        val sign = if (deltaHz >= 0) "+" else ""
        val absDelta = kotlin.math.abs(deltaHz)
        
        return when {
            absDelta >= 1_000_000 -> String.format("$sign%.3f MHz", absDelta / 1_000_000.0)
            absDelta >= 1_000 -> String.format("$sign%.3f kHz", absDelta / 1_000.0)
            else -> String.format("$sign%.1f Hz", absDelta)
        }
    }

    /**
     * 将MHz字符串转换为Hz
     * @param mhzString MHz值字符串
     * @return Hz值，转换失败返回null
     */
    fun mhzToHz(mhzString: String): Long? {
        return try {
            (mhzString.toDouble() * 1_000_000).toLong()
        } catch (e: Exception) {
            LogManager.e("FrequencyFormatter", "MHz转换失败: $mhzString", e)
            null
        }
    }

    /**
     * 将Hz转换为MHz字符串
     * @param hz Hz值
     * @param precision 小数精度
     * @return MHz字符串
     */
    @JvmOverloads
    fun hzToMhz(hz: Long, precision: Int = 6): String {
        return String.format("%.${precision}f", hz / 1_000_000.0)
    }
}

/**
 * Long类型的频率格式化扩展函数
 * @param precision 小数精度（MHz单位时强制使用6位）
 * @return 格式化后的字符串
 */
fun Long.toFrequencyString(precision: Int = 3): String =
    FrequencyFormatter.format(this, precision)

/**
 * Double类型的频率格式化扩展函数
 * @param precision 小数精度（MHz单位时强制使用6位）
 * @return 格式化后的字符串
 */
fun Double.toFrequencyString(precision: Int = 3): String =
    FrequencyFormatter.format(this, precision)

/**
 * 格式化频率范围的扩展函数
 * @param highHz 最高频率
 * @param precision 小数精度（MHz单位时强制使用6位）
 * @return 格式化后的范围字符串
 */
fun Long.toFrequencyRangeString(highHz: Long?, precision: Int = 3): String =
    FrequencyFormatter.formatRange(this, highHz, precision)

/**
 * 格式化多普勒补偿频率的扩展函数
 * @param dopplerShiftHz 多普勒频移
 * @param precision 小数精度（MHz单位时强制使用6位）
 * @return 补偿后的频率字符串
 */
fun Long.toDopplerFrequencyString(dopplerShiftHz: Double, precision: Int = 3): String =
    FrequencyFormatter.formatWithDoppler(this, dopplerShiftHz, precision)
