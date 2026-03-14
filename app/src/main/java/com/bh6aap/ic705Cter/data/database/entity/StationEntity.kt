package com.bh6aap.ic705Cter.data.database.entity

/**
 * 地面站（观测点）数据实体类
 * 存储地面站的位置信息
 */
data class StationEntity(
    // 主键ID
    val id: Long = 0,

    // 地面站名称
    val name: String,

    // 纬度（度）
    val latitude: Double,

    // 经度（度）
    val longitude: Double,

    // 海拔高度（米）
    val altitude: Double = 0.0,

    // 是否设为默认地面站
    val isDefault: Boolean = false,

    // 最小仰角（度）- 用于计算卫星可见性
    val minElevation: Double = 5.0,

    // 自定义备注
    val notes: String? = null,

    // 数据创建时间
    val createdAt: Long = System.currentTimeMillis(),

    // 数据更新时间
    val updatedAt: Long = System.currentTimeMillis()
)
