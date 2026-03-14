package com.bh6aap.ic705Cter.util

/**
 * 频率格式化工具类
 * 提供各种频率格式化方法
 */
object FrequencyFormatUtils {

    /**
     * 格式化频率范围
     */
    fun formatRange(lowHz: Long?, highHz: Long?): String =
        FrequencyFormatter.formatRange(lowHz, highHz)

    /**
     * 格式化频率显示
     */
    fun formatFrequency(frequencyHz: Long): String =
        frequencyHz.toFrequencyString()

    /**
     * 格式化带多普勒补偿的频率显示
     */
    fun formatWithDoppler(frequencyHz: Long, dopplerShiftHz: Double): String =
        FrequencyFormatter.formatWithDoppler(frequencyHz, dopplerShiftHz)

    /**
     * 格式化Double类型的频率显示（用于实时计算频率）
     */
    fun formatDouble(frequencyHz: Double, precision: Int = 6): String =
        frequencyHz.toFrequencyString(precision = precision)

    /**
     * 格式化频率显示（不带单位，用于卫星跟踪界面）
     * @param frequencyHz 频率值（赫兹）
     * @param precision 小数精度，默认为6位
     * @return 格式化后的字符串，如 "435.500000"
     */
    fun formatWithoutUnit(frequencyHz: Double, precision: Int = 6): String {
        val mhzValue = frequencyHz / 1_000_000.0
        return String.format("%.${precision}f", mhzValue)
    }
}

/**
 * 扩展函数：格式化频率（不带单位）
 */
fun formatFrequencyWithoutUnit(frequencyHz: Double, precision: Int = 6): String {
    return FrequencyFormatUtils.formatWithoutUnit(frequencyHz, precision)
}
