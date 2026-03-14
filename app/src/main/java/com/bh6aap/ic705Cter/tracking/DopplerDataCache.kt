package com.bh6aap.ic705Cter.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 多普勒数据缓存
 * 全局单例，用于在Orekit计算和跟踪控制器之间共享多普勒数据
 */
object DopplerDataCache {

    // 多普勒变化限制（Hz）- 单次变化不超过30kHz
    private const val MAX_DOPPLER_CHANGE_HZ = 30000.0

    // 当前多普勒频移（Hz）
    private val _dopplerShift = MutableStateFlow(0.0)
    val dopplerShift: StateFlow<Double> = _dopplerShift.asStateFlow()
    
    // 距离变化率（m/s）
    private val _rangeRate = MutableStateFlow(0.0)
    val rangeRate: StateFlow<Double> = _rangeRate.asStateFlow()
    
    // 卫星距离（km）
    private val _distance = MutableStateFlow(0.0)
    val distance: StateFlow<Double> = _distance.asStateFlow()
    
    // 方位角
    private val _azimuth = MutableStateFlow(0.0)
    val azimuth: StateFlow<Double> = _azimuth.asStateFlow()
    
    // 仰角
    private val _elevation = MutableStateFlow(0.0)
    val elevation: StateFlow<Double> = _elevation.asStateFlow()
    
    // 目标卫星NORAD ID
    private val _targetSatelliteId = MutableStateFlow<String?>(null)
    val targetSatelliteId: StateFlow<String?> = _targetSatelliteId.asStateFlow()
    
    /**
     * 更新多普勒数据
     * 带有限幅滤波器，单次变化不超过30kHz
     * 同时更新预测性多普勒计算器
     */
    fun updateData(
        dopplerShiftHz: Double,
        rangeRateMps: Double,
        distanceKm: Double,
        azimuthDeg: Double,
        elevationDeg: Double,
        satelliteId: String? = null
    ) {
        // 计算多普勒变化量
        val dopplerChange = dopplerShiftHz - _dopplerShift.value

        // 限制单次变化不超过30kHz
        val limitedDoppler = if (kotlin.math.abs(dopplerChange) > MAX_DOPPLER_CHANGE_HZ) {
            _dopplerShift.value + (MAX_DOPPLER_CHANGE_HZ * kotlin.math.sign(dopplerChange))
        } else {
            dopplerShiftHz
        }

        _dopplerShift.value = limitedDoppler
        _rangeRate.value = rangeRateMps
        _distance.value = distanceKm
        _azimuth.value = azimuthDeg
        _elevation.value = elevationDeg
        satelliteId?.let { _targetSatelliteId.value = it }

        // 更新预测性多普勒计算器
        PredictiveDopplerCalculator.updateDopplerData(limitedDoppler, rangeRateMps)
    }
    
    /**
     * 清除数据
     */
    fun clear() {
        _dopplerShift.value = 0.0
        _rangeRate.value = 0.0
        _distance.value = 0.0
        _azimuth.value = 0.0
        _elevation.value = 0.0
        _targetSatelliteId.value = null
    }
    
    /**
     * 获取当前多普勒频移（线程安全）
     */
    fun getCurrentDopplerShift(): Double = _dopplerShift.value
    
    /**
     * 获取当前距离变化率（线程安全）
     */
    fun getCurrentRangeRate(): Double = _rangeRate.value
}
