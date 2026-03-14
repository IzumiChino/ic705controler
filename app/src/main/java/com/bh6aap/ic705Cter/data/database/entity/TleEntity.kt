package com.bh6aap.ic705Cter.data.database.entity

/**
 * TLE数据实体类
 * 用于存储卫星TLE轨道数据
 */
data class TleEntity(
    val id: Long = 0,
    val noradId: String,           // NORAD卫星编号
    val tleLine1: String,          // TLE第一行
    val tleLine2: String,          // TLE第二行
    val epoch: String? = null,     // TLE历元时间
    val name: String? = null,      // 卫星名称（可选）
    val updatedAt: Long = System.currentTimeMillis()
)
