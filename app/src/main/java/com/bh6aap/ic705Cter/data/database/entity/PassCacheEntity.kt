package com.bh6aap.ic705Cter.data.database.entity

import android.content.ContentValues
import com.bh6aap.ic705Cter.util.SatellitePassCalculator

/**
 * 卫星过境缓存实体
 * 用于缓存过境计算结果，避免重复计算
 */
data class PassCacheEntity(
    val id: Long = 0,
    val noradId: String,              // 卫星NORAD ID
    val name: String,                 // 卫星名称
    val aosTime: Long,                // 入境时间（毫秒）
    val losTime: Long,                // 出境时间（毫秒）
    val maxElevation: Double,         // 最大仰角（度）
    val aosAzimuth: Double,           // 入境方位角（度）
    val losAzimuth: Double,           // 出境方位角（度）
    val duration: Long,               // 过境时长（秒）
    val maxElevationTime: Long,       // 最大仰角时间（毫秒）
    val stationLat: Double,           // 计算时的地面站纬度
    val stationLon: Double,           // 计算时的地面站经度
    val stationAlt: Double,           // 计算时的地面站高度
    val minElevation: Double,         // 最小仰角
    val calculatedAt: Long,           // 计算时间（毫秒）
    val hoursAhead: Int               // 预测小时数
) {
    fun toContentValues(): ContentValues {
        return ContentValues().apply {
            put("noradId", noradId)
            put("name", name)
            put("aosTime", aosTime)
            put("losTime", losTime)
            put("maxElevation", maxElevation)
            put("aosAzimuth", aosAzimuth)
            put("losAzimuth", losAzimuth)
            put("duration", duration)
            put("maxElevationTime", maxElevationTime)
            put("stationLat", stationLat)
            put("stationLon", stationLon)
            put("stationAlt", stationAlt)
            put("minElevation", minElevation)
            put("calculatedAt", calculatedAt)
            put("hoursAhead", hoursAhead)
        }
    }

    /**
     * 转换为SatellitePass
     */
    fun toSatellitePass(): SatellitePassCalculator.SatellitePass {
        return SatellitePassCalculator.SatellitePass(
            noradId = noradId,
            name = name,
            aosTime = aosTime,
            losTime = losTime,
            maxElevation = maxElevation,
            aosAzimuth = aosAzimuth,
            losAzimuth = losAzimuth,
            duration = duration,
            maxElevationTime = maxElevationTime
        )
    }
}
