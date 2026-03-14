package com.bh6aap.ic705Cter.data.database.entity

/**
 * 转发器实体类
 * 用于存储从API获取的转发器数据
 */
data class TransmitterEntity(
    val id: Long = 0,
    val uuid: String,              // 转发器唯一标识
    val noradId: Int,              // 卫星NORAD ID
    val description: String?,      // 转发器描述
    val uplinkLow: Long?,          // 上行频率下限(Hz)
    val uplinkHigh: Long?,         // 上行频率上限(Hz)
    val downlinkLow: Long?,        // 下行频率下限(Hz)
    val downlinkHigh: Long?,       // 下行频率上限(Hz)
    val mode: String?,             // 模式(如FM, SSB等)
    val uplinkMode: String?,       // 上行模式
    val invert: Boolean = false,   // 是否反向
    val updatedAt: Long = System.currentTimeMillis()
)
