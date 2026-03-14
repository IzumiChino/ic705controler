package com.bh6aap.ic705Cter.tracking

import com.bh6aap.ic705Cter.util.LogManager

/**
 * 预测性多普勒计算器
 * 解决从App到设备通信延迟导致的频率不准确问题
 *
 * 核心思想：计算未来时间点的多普勒频移，而不是当前时间
 */
object PredictiveDopplerCalculator {

    private const val TAG = "PredictiveDoppler"

    // 默认通信延迟（毫秒）- 蓝牙通信典型延迟
    private const val DEFAULT_COMMUNICATION_DELAY_MS = 300L

    // 最小预测时间（毫秒）- 避免过于频繁的预测
    private const val MIN_PREDICTION_INTERVAL_MS = 100L

    // 历史数据用于计算多普勒变化率
    private data class DopplerHistory(
        val timestamp: Long,
        val dopplerShift: Double,
        val rangeRate: Double
    )

    private val dopplerHistory = ArrayDeque<DopplerHistory>(10)
    private const val MAX_HISTORY_SIZE = 5

    // 上次预测时间
    private var lastPredictionTime = 0L

    // 预测的多普勒频移
    private var predictedDopplerShift = 0.0
    private var predictedRangeRate = 0.0

    /**
     * 更新多普勒历史数据
     */
    fun updateDopplerData(dopplerShiftHz: Double, rangeRateMps: Double) {
        val now = System.currentTimeMillis()

        // 添加新数据
        dopplerHistory.addLast(DopplerHistory(now, dopplerShiftHz, rangeRateMps))

        // 限制历史数据大小
        if (dopplerHistory.size > MAX_HISTORY_SIZE) {
            dopplerHistory.removeFirst()
        }

        // 计算预测值
        calculatePrediction(now)
    }

    /**
     * 计算预测的多普勒频移
     * 基于历史数据线性外推
     */
    private fun calculatePrediction(currentTime: Long) {
        if (dopplerHistory.size < 2) {
            // 数据不足，使用当前值
            predictedDopplerShift = dopplerHistory.lastOrNull()?.dopplerShift ?: 0.0
            predictedRangeRate = dopplerHistory.lastOrNull()?.rangeRate ?: 0.0
            return
        }

        // 计算多普勒变化率（Hz/ms）
        val recent = dopplerHistory.takeLast(3) // 使用最近3个数据点
        if (recent.size >= 2) {
            val first = recent.first()
            val last = recent.last()
            val timeDelta = last.timestamp - first.timestamp

            if (timeDelta > 0) {
                val dopplerDelta = last.dopplerShift - first.dopplerShift
                val dopplerRateHzPerMs = dopplerDelta / timeDelta

                // 预测未来时间点的多普勒频移
                val predictionTime = currentTime + DEFAULT_COMMUNICATION_DELAY_MS
                val timeToPredict = predictionTime - last.timestamp
                predictedDopplerShift = last.dopplerShift + (dopplerRateHzPerMs * timeToPredict)

                // 同样预测径向速度
                val rangeRateDelta = last.rangeRate - first.rangeRate
                val rangeRatePerMs = rangeRateDelta / timeDelta
                predictedRangeRate = last.rangeRate + (rangeRatePerMs * timeToPredict)

                LogManager.d(TAG, "多普勒预测: 当前=${last.dopplerShift.toInt()}Hz, " +
                    "变化率=${(dopplerRateHzPerMs * 1000).toInt()}Hz/s, " +
                    "预测=${predictedDopplerShift.toInt()}Hz (提前${DEFAULT_COMMUNICATION_DELAY_MS}ms)")
            }
        }

        lastPredictionTime = currentTime
    }

    /**
     * 获取预测的多普勒频移（用于频率计算）
     */
    fun getPredictedDopplerShift(): Double = predictedDopplerShift

    /**
     * 获取预测的径向速度（用于频率计算）
     */
    fun getPredictedRangeRate(): Double = predictedRangeRate

    /**
     * 清除历史数据
     */
    fun clear() {
        dopplerHistory.clear()
        predictedDopplerShift = 0.0
        predictedRangeRate = 0.0
        lastPredictionTime = 0L
    }

    /**
     * 计算预测性的地面接收频率（下行）
     * @param satelliteFreqHz 卫星发射频率（Hz）
     * @return 预测的地面接收频率（Hz）
     */
    fun calculatePredictedGroundDownlink(satelliteFreqHz: Double): Double {
        return DopplerCalculator.calculateGroundDownlink(satelliteFreqHz, predictedRangeRate)
    }

    /**
     * 计算预测性的地面发射频率（上行）
     * @param satelliteFreqHz 卫星接收频率（Hz）
     * @return 预测的地面发射频率（Hz）
     */
    fun calculatePredictedGroundUplink(satelliteFreqHz: Double): Double {
        return DopplerCalculator.calculateGroundUplink(satelliteFreqHz, predictedRangeRate)
    }

    /**
     * 计算预测性的线性卫星地面频率对
     */
    fun calculatePredictedLinearSatelliteFrequencies(
        satelliteDownlinkHz: Double,
        satelliteUplinkHz: Double
    ): Pair<Double, Double> {
        return DopplerCalculator.calculateLinearSatelliteFrequencies(
            satelliteDownlinkHz,
            satelliteUplinkHz,
            predictedRangeRate
        )
    }

    /**
     * 计算预测性的FM卫星地面频率对
     */
    fun calculatePredictedFMSatelliteFrequencies(
        satelliteDownlinkHz: Double,
        satelliteUplinkHz: Double
    ): Pair<Double, Double> {
        return DopplerCalculator.calculateFMSatelliteFrequencies(
            satelliteDownlinkHz,
            satelliteUplinkHz,
            predictedRangeRate
        )
    }
}
