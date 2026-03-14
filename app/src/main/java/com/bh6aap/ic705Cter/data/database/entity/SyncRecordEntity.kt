package com.bh6aap.ic705Cter.data.database.entity

/**
 * 数据同步记录实体类
 * 记录各种数据的同步时间和状态
 */
data class SyncRecordEntity(
    // 主键ID
    val id: Long = 0,

    // 同步类型（如：tle_satnogs, tle_celestrak 等）
    val syncType: String,

    // 同步时间戳
    val syncTime: Long,

    // 同步记录数量
    val recordCount: Int = 0,

    // 数据来源
    val source: String? = null,

    // 同步状态（成功/失败）
    val isSuccess: Boolean = true,

    // 错误信息（如果失败）
    val errorMessage: String? = null
)
