package com.bh6aap.ic705Cter.data.database.entity

/**
 * 备忘录实体类
 * 用于存储卫星跟踪时的呼号记录和备注
 */
data class MemoEntity(
    // 主键ID
    val id: Long = 0,

    // 备忘录内容（呼号或备注）
    val content: String,

    // 当前跟踪的卫星名称
    val satelliteName: String? = null,

    // 当前跟踪的卫星ID
    val satelliteId: String? = null,

    // 本地时间戳（毫秒）
    val localTime: Long = System.currentTimeMillis(),

    // UTC时间戳（毫秒）
    val utcTime: Long = System.currentTimeMillis(),

    // 创建时间
    val createdAt: Long = System.currentTimeMillis()
)
