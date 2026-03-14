package com.bh6aap.ic705Cter.data.database.entity

/**
 * 卫星数据实体类
 * 存储卫星的基本信息和 TLE 数据
 */
data class SatelliteEntity(
    // 主键ID
    val id: Long = 0,

    // 卫星名称
    val name: String,

    // NORAD 卫星编号
    val noradId: String,

    // 国际标识符
    val internationalDesignator: String? = null,

    // TLE 第一行
    val tleLine1: String,

    // TLE 第二行
    val tleLine2: String,

    // 卫星类别（业余、气象、导航等）
    val category: String? = null,

    // 是否收藏
    val isFavorite: Boolean = false,

    // 自定义备注
    val notes: String? = null,

    // 数据创建时间
    val createdAt: Long = System.currentTimeMillis(),

    // 数据更新时间
    val updatedAt: Long = System.currentTimeMillis()
)
