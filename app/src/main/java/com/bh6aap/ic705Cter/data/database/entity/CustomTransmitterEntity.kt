package com.bh6aap.ic705Cter.data.database.entity

/**
 * 用户自定义转发器实体类
 * 用于存储用户对转发器的自定义修改
 */
data class CustomTransmitterEntity(
    // 主键ID
    val id: Long = 0,

    // 转发器UUID（用于关联API数据）
    val uuid: String,

    // 卫星NORAD ID（用于关联卫星）
    val noradCatId: Int,

    // 转发器描述（用户可修改）
    val description: String? = null,

    // 上行频率下限（Hz，用户可修改）
    val uplinkLow: Long? = null,

    // 上行频率上限（Hz，用户可修改）
    val uplinkHigh: Long? = null,

    // 下行频率下限（Hz，用户可修改）
    val downlinkLow: Long? = null,

    // 下行频率上限（Hz，用户可修改）
    val downlinkHigh: Long? = null,

    // 下行模式（用户可修改）
    val downlinkMode: String? = null,

    // 上行模式（用户可修改）
    val uplinkMode: String? = null,

    // 是否启用自定义设置
    val isEnabled: Boolean = true,

    // 创建时间
    val createdAt: Long = System.currentTimeMillis(),

    // 更新时间
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 获取上行频率下限（MHz）
     */
    fun getUplinkLowMhz(): Double? = uplinkLow?.let { it / 1_000_000.0 }

    /**
     * 获取上行频率上限（MHz）
     */
    fun getUplinkHighMhz(): Double? = uplinkHigh?.let { it / 1_000_000.0 }

    /**
     * 获取下行频率下限（MHz）
     */
    fun getDownlinkLowMhz(): Double? = downlinkLow?.let { it / 1_000_000.0 }

    /**
     * 获取下行频率上限（MHz）
     */
    fun getDownlinkHighMhz(): Double? = downlinkHigh?.let { it / 1_000_000.0 }

    companion object {
        /**
         * 从MHz创建实体
         */
        fun fromMhz(
            uuid: String,
            noradCatId: Int,
            description: String? = null,
            uplinkLowMhz: Double? = null,
            uplinkHighMhz: Double? = null,
            downlinkLowMhz: Double? = null,
            downlinkHighMhz: Double? = null,
            downlinkMode: String? = null,
            uplinkMode: String? = null
        ): CustomTransmitterEntity {
            return CustomTransmitterEntity(
                uuid = uuid,
                noradCatId = noradCatId,
                description = description,
                uplinkLow = uplinkLowMhz?.let { (it * 1_000_000).toLong() },
                uplinkHigh = uplinkHighMhz?.let { (it * 1_000_000).toLong() },
                downlinkLow = downlinkLowMhz?.let { (it * 1_000_000).toLong() },
                downlinkHigh = downlinkHighMhz?.let { (it * 1_000_000).toLong() },
                downlinkMode = downlinkMode,
                uplinkMode = uplinkMode
            )
        }
    }
}
