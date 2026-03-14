package com.bh6aap.ic705Cter.data.database.entity

/**
 * 频率预设实体类
 * 用于存储线性卫星的自定义频率预设
 */
data class FrequencyPresetEntity(
    // 主键ID
    val id: Long = 0,

    // 卫星NORAD ID（用于关联卫星）
    val noradId: String,

    // 预设名称
    val name: String,

    // 上行频率（Hz）
    val uplinkFreqHz: Long,

    // 下行频率（Hz）
    val downlinkFreqHz: Long,

    // 排序顺序（用于拖动排序）
    val sortOrder: Int = 0,

    // 创建时间
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * 获取上行频率（MHz）
     */
    fun getUplinkMhz(): Double = uplinkFreqHz / 1_000_000.0

    /**
     * 获取下行频率（MHz）
     */
    fun getDownlinkMhz(): Double = downlinkFreqHz / 1_000_000.0

    companion object {
        /**
         * 从MHz创建实体
         */
        fun fromMhz(
            noradId: String,
            name: String,
            uplinkMhz: Double,
            downlinkMhz: Double
        ): FrequencyPresetEntity {
            return FrequencyPresetEntity(
                noradId = noradId,
                name = name,
                uplinkFreqHz = (uplinkMhz * 1_000_000).toLong(),
                downlinkFreqHz = (downlinkMhz * 1_000_000).toLong()
            )
        }
    }
}
