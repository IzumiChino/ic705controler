package com.bh6aap.ic705Cter.data.database.entity

/**
 * 卫星过境预测数据实体类
 * 存储计算出的卫星出入境时间
 */
data class PassPredictionEntity(
    // 主键ID
    val id: Long = 0,

    // 关联的卫星 ID
    val satelliteId: Long,

    // 关联的地面站 ID
    val stationId: Long,

    // 入境时间（Acquisition of Signal）- 时间戳
    val aosTime: Long,

    // 入境时的方位角（度）
    val aosAzimuth: Double,

    // 入境时的仰角（度）
    val aosElevation: Double,

    // 出境时间（Loss of Signal）- 时间戳
    val losTime: Long,

    // 出境时的方位角（度）
    val losAzimuth: Double,

    // 出境时的仰角（度）
    val losElevation: Double,

    // 最大仰角时间 - 时间戳
    val maxElevationTime: Long,

    // 最大仰角（度）
    val maxElevation: Double,

    // 过境持续时间（秒）
    val duration: Long,

    // 是否已提醒
    val isNotified: Boolean = false,

    // 是否已跟踪记录
    val isTracked: Boolean = false,

    // 数据创建时间
    val createdAt: Long = System.currentTimeMillis()
)
