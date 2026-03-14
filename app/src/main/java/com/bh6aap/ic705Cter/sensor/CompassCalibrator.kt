package com.bh6aap.ic705Cter.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.bh6aap.ic705Cter.util.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

/**
 * 指南针校准管理器
 * 检测用户动作，引导完成3圈"8"字环绕，自动计算校准参数
 */
class CompassCalibrator(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // 校准阶段
    sealed class CalibrationPhase {
        object WaitingForMotion : CalibrationPhase()      // 等待用户开始移动
        object DetectingFigure8 : CalibrationPhase()      // 检测8字动作
        object Calculating : CalibrationPhase()           // 计算校准参数
        object Completed : CalibrationPhase()             // 校准完成
        object Failed : CalibrationPhase()                // 校准失败
    }

    // 当前阶段
    private val _currentPhase = MutableStateFlow<CalibrationPhase>(CalibrationPhase.WaitingForMotion)
    val currentPhase: StateFlow<CalibrationPhase> = _currentPhase

    // 8字环绕进度（0-3圈）
    private val _figure8Progress = MutableStateFlow(0)
    val figure8Progress: StateFlow<Int> = _figure8Progress

    // 校准质量 0-100
    private val _calibrationQuality = MutableStateFlow(0)
    val calibrationQuality: StateFlow<Int> = _calibrationQuality

    // 状态描述
    private val _statusText = MutableStateFlow("请开始移动手机")
    val statusText: StateFlow<String> = _statusText

    // 传感器数据缓冲区
    private val magneticReadings = mutableListOf<Triple<Float, Float, Float>>()
    private val accelReadings = mutableListOf<Triple<Float, Float, Float>>()
    private val gyroReadings = mutableListOf<Triple<Float, Float, Float>>()
    private val maxReadings = 500

    // 8字检测相关
    private var figure8Count = 0
    private var lastCrossingDirection = 0  // -1=下穿上, 1=上穿下, 0=无
    private var lastPitchSign = 0
    private var motionDetected = false
    private var stationaryTime = 0L
    private val motionThreshold = 2.0f  // 运动检测阈值 m/s²
    
    // 改进的8字检测 - 使用状态机
    private var figure8State = Figure8State.IDLE
    private var stateEntryTime = 0L
    private var lastFigure8Time = 0L
    private val minStateDuration = 200L  // 每个状态最小持续时间(ms)
    private val maxFigure8Time = 8000L   // 单圈8字最大时间(ms)
    
    // 8字动作状态
    enum class Figure8State {
        IDLE,           // 空闲/水平
        PITCH_UP,       // 上仰
        PITCH_DOWN,     // 下俯
        ROLL_LEFT,      // 左倾
        ROLL_RIGHT      // 右倾
    }

    // 校准参数
    private var hardIronOffset = Triple(0f, 0f, 0f)
    private var softIronMatrix = Array(3) { FloatArray(3) { 0f } }

    // 当前传感器值
    private var currentMagnetic = FloatArray(3)
    private var currentAccel = FloatArray(3)
    private var currentGyro = FloatArray(3)

    // 采样计数
    private var sampleCount = 0
    private var lastUpdateTime = 0L

    // 校准完成回调
    private var onCalibrationComplete: ((Boolean, CalibrationResult) -> Unit)? = null

    data class CalibrationResult(
        val success: Boolean,
        val quality: Int,
        val figure8Count: Int,
        val hardIronOffset: Triple<Float, Float, Float>,
        val softIronMatrix: Array<FloatArray>,
        val message: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as CalibrationResult
            return success == other.success &&
                    quality == other.quality &&
                    figure8Count == other.figure8Count &&
                    hardIronOffset == other.hardIronOffset &&
                    message == other.message &&
                    softIronMatrix.contentDeepEquals(other.softIronMatrix)
        }

        override fun hashCode(): Int {
            var result = success.hashCode()
            result = 31 * result + quality
            result = 31 * result + figure8Count
            result = 31 * result + hardIronOffset.hashCode()
            result = 31 * result + softIronMatrix.contentDeepHashCode()
            result = 31 * result + message.hashCode()
            return result
        }
    }

    /**
     * 开始校准
     */
    fun startCalibration(onComplete: ((Boolean, CalibrationResult) -> Unit)? = null) {
        if (_currentPhase.value is CalibrationPhase.DetectingFigure8 ||
            _currentPhase.value is CalibrationPhase.Calculating) {
            LogManager.w(TAG, "校准已在进行中")
            return
        }

        this.onCalibrationComplete = onComplete

        // 重置所有状态
        resetState()
        _currentPhase.value = CalibrationPhase.WaitingForMotion
        _statusText.value = "请开始移动手机，做\"8\"字环绕"

        // 注册传感器
        magneticSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscopeSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        lastUpdateTime = System.currentTimeMillis()
        LogManager.i(TAG, "开始指南针校准，等待用户动作")
    }

    /**
     * 重置状态
     */
    private fun resetState() {
        magneticReadings.clear()
        accelReadings.clear()
        gyroReadings.clear()
        figure8Count = 0
        _figure8Progress.value = 0
        _calibrationQuality.value = 0
        lastCrossingDirection = 0
        lastPitchSign = 0
        motionDetected = false
        stationaryTime = 0
        sampleCount = 0
        hardIronOffset = Triple(0f, 0f, 0f)
        softIronMatrix = Array(3) { FloatArray(3) { 0f } }
        figure8State = Figure8State.IDLE
        stateEntryTime = 0
        lastFigure8Time = 0
        resetFigure8Detection(0)
    }

    /**
     * 停止校准
     */
    fun stopCalibration() {
        sensorManager.unregisterListener(this)
        if (_currentPhase.value !is CalibrationPhase.Completed &&
            _currentPhase.value !is CalibrationPhase.Failed) {
            _currentPhase.value = CalibrationPhase.WaitingForMotion
        }
        LogManager.i(TAG, "校准已停止")
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_MAGNETIC_FIELD -> {
                currentMagnetic = event.values.clone()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                currentAccel = event.values.clone()
                processAccelData()
            }
            Sensor.TYPE_GYROSCOPE -> {
                currentGyro = event.values.clone()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    /**
     * 处理加速度数据，检测动作
     */
    private fun processAccelData() {
        val currentTime = System.currentTimeMillis()
        val deltaTime = currentTime - lastUpdateTime
        lastUpdateTime = currentTime

        when (_currentPhase.value) {
            is CalibrationPhase.WaitingForMotion -> checkForMotionStart()
            is CalibrationPhase.DetectingFigure8 -> detectFigure8Motion()
            else -> {}
        }
    }

    /**
     * 检测用户是否开始移动
     */
    private fun checkForMotionStart() {
        // 计算加速度变化量
        val accelMagnitude = sqrt(
            currentAccel[0] * currentAccel[0] +
            currentAccel[1] * currentAccel[1] +
            currentAccel[2] * currentAccel[2]
        )

        // 重力加速度约9.8，检测变化量
        val deltaFromGravity = abs(accelMagnitude - 9.8f)

        if (deltaFromGravity > motionThreshold) {
            motionDetected = true
            stationaryTime = 0

            // 开始记录数据
            recordSensorData()

            // 进入8字检测阶段
            _currentPhase.value = CalibrationPhase.DetectingFigure8
            _statusText.value = "检测到动作，请继续\"8\"字环绕（0/3）"
            LogManager.i(TAG, "检测到用户动作，开始8字检测")
        }
    }

    /**
     * 检测8字环绕动作 - 简化的基于角度变化的检测
     * 8字动作特征：手机在空中画8字形，导致俯仰角和横滚角周期性变化
     */
    private fun detectFigure8Motion() {
        // 记录传感器数据
        recordSensorData()

        val currentTime = System.currentTimeMillis()
        
        // 计算俯仰角和横滚角
        val pitch = calculatePitch(currentAccel)
        val roll = calculateRoll(currentAccel)
        
        // 检测8字动作 - 基于角度变化的简单检测
        detectFigure8ByAngleChange(pitch, roll, currentTime)

        // 检测静止超时
        val accelMagnitude = sqrt(
            currentAccel[0] * currentAccel[0] +
            currentAccel[1] * currentAccel[1] +
            currentAccel[2] * currentAccel[2]
        )
        val deltaFromGravity = abs(accelMagnitude - 9.8f)

        if (deltaFromGravity < motionThreshold / 2) {
            stationaryTime += 50
            if (stationaryTime > 3000) {
                if (figure8Count < 3) {
                    _statusText.value = "动作停止，请继续\"8\"字环绕（$figure8Count/3）"
                }
            }
        } else {
            stationaryTime = 0
        }
    }
    
    // 8字检测变量
    private var maxPitchReached = 0f
    private var minPitchReached = 0f
    private var maxRollReached = 0f
    private var minRollReached = 0f
    private var figure8StartTime = 0L
    private var hasUp = false
    private var hasDown = false
    private var hasLeft = false
    private var hasRight = false
    
    /**
     * 基于角度变化检测8字
     * 简化算法：检测俯仰和横滚是否都经历了正负变化
     */
    private fun detectFigure8ByAngleChange(pitch: Float, roll: Float, currentTime: Long) {
        val pitchThreshold = 15f  // 俯仰角阈值
        val rollThreshold = 15f   // 横滚角阈值
        
        // 初始化
        if (figure8StartTime == 0L) {
            figure8StartTime = currentTime
            maxPitchReached = pitch
            minPitchReached = pitch
            maxRollReached = roll
            minRollReached = roll
        }
        
        // 记录最大最小角度
        if (pitch > maxPitchReached) maxPitchReached = pitch
        if (pitch < minPitchReached) minPitchReached = pitch
        if (roll > maxRollReached) maxRollReached = roll
        if (roll < minRollReached) minRollReached = roll
        
        // 检测是否达到各个方向
        if (pitch > pitchThreshold) hasUp = true
        if (pitch < -pitchThreshold) hasDown = true
        if (roll > rollThreshold) hasRight = true
        if (roll < -rollThreshold) hasLeft = true
        
        // 检查是否完成一个8字
        val duration = currentTime - figure8StartTime
        val hasAllDirections = hasUp && hasDown && hasLeft && hasRight
        val hasEnoughRange = (maxPitchReached - minPitchReached) > pitchThreshold * 2 &&
                            (maxRollReached - minRollReached) > rollThreshold * 2
        
        // 完成条件：包含所有方向、角度范围足够、时间在合理范围内
        if (hasAllDirections && hasEnoughRange && duration in 500..5000) {
            figure8Count++
            _figure8Progress.value = figure8Count
            
            LogManager.d(TAG, "完成8字环绕 #$figure8Count, 耗时=${duration}ms, " +
                "pitch范围=${minPitchReached.toInt()}~${maxPitchReached.toInt()}, " +
                "roll范围=${minRollReached.toInt()}~${maxRollReached.toInt()}")
            
            val progressText = when {
                figure8Count < 3 -> "请继续\"8\"字环绕（$figure8Count/3）"
                figure8Count == 3 -> "完成！正在计算校准参数..."
                else -> "超出目标，继续优化..."
            }
            _statusText.value = progressText

            // 重置检测状态
            resetFigure8Detection(currentTime)

            // 完成3圈后进入计算阶段
            if (figure8Count >= 3) {
                _currentPhase.value = CalibrationPhase.Calculating
                _statusText.value = "正在计算校准参数..."
                performCalibration()
            }
        }
        
        // 如果超时未完成的8字，重置检测
        if (duration > 5000) {
            LogManager.d(TAG, "8字检测超时，重置")
            resetFigure8Detection(currentTime)
        }
    }
    
    /**
     * 重置8字检测状态
     */
    private fun resetFigure8Detection(currentTime: Long) {
        figure8StartTime = currentTime
        maxPitchReached = 0f
        minPitchReached = 0f
        maxRollReached = 0f
        minRollReached = 0f
        hasUp = false
        hasDown = false
        hasLeft = false
        hasRight = false
    }

    /**
     * 计算俯仰角
     */
    private fun calculatePitch(accel: FloatArray): Float {
        // 俯仰角 = arctan2(-x, sqrt(y² + z²))
        val pitch = atan2(-accel[0].toDouble(), sqrt((accel[1] * accel[1] + accel[2] * accel[2]).toDouble()))
        return Math.toDegrees(pitch).toFloat()
    }
    
    /**
     * 计算横滚角
     */
    private fun calculateRoll(accel: FloatArray): Float {
        // 横滚角 = arctan2(y, z)
        val roll = atan2(accel[1].toDouble(), accel[2].toDouble())
        return Math.toDegrees(roll).toFloat()
    }

    /**
     * 记录传感器数据
     */
    private fun recordSensorData() {
        sampleCount++

        magneticReadings.add(Triple(currentMagnetic[0], currentMagnetic[1], currentMagnetic[2]))
        accelReadings.add(Triple(currentAccel[0], currentAccel[1], currentAccel[2]))
        gyroReadings.add(Triple(currentGyro[0], currentGyro[1], currentGyro[2]))

        // 限制缓冲区大小
        if (magneticReadings.size > maxReadings) {
            magneticReadings.removeAt(0)
            accelReadings.removeAt(0)
            gyroReadings.removeAt(0)
        }
    }

    /**
     * 执行校准计算
     */
    private fun performCalibration() {
        try {
            if (magneticReadings.size < 100) {
                _currentPhase.value = CalibrationPhase.Failed
                val result = CalibrationResult(
                    success = false,
                    quality = 0,
                    figure8Count = figure8Count,
                    hardIronOffset = Triple(0f, 0f, 0f),
                    softIronMatrix = Array(3) { FloatArray(3) { 0f } },
                    message = "数据不足，请重新校准"
                )
                onCalibrationComplete?.invoke(false, result)
                stopCalibration()
                return
            }

            // 计算硬铁偏移
            calculateHardIronOffset()

            // 计算软铁校准矩阵
            calculateSoftIronMatrix()

            // 计算最终质量
            val finalQuality = calculateFinalQuality()
            _calibrationQuality.value = finalQuality

            val success = finalQuality >= 60
            _currentPhase.value = if (success) CalibrationPhase.Completed else CalibrationPhase.Failed

            val result = CalibrationResult(
                success = success,
                quality = finalQuality,
                figure8Count = figure8Count,
                hardIronOffset = hardIronOffset,
                softIronMatrix = softIronMatrix,
                message = if (success) "校准成功，完成${figure8Count}圈\"8\"字环绕" else "校准质量较低，建议重新校准"
            )

            _statusText.value = result.message
            LogManager.i(TAG, "校准完成: 质量=$finalQuality%, 8字圈数=$figure8Count, 成功=$success")
            onCalibrationComplete?.invoke(success, result)
            stopCalibration()

        } catch (e: Exception) {
            LogManager.e(TAG, "校准计算失败", e)
            _currentPhase.value = CalibrationPhase.Failed
            val result = CalibrationResult(
                success = false,
                quality = 0,
                figure8Count = figure8Count,
                hardIronOffset = Triple(0f, 0f, 0f),
                softIronMatrix = Array(3) { FloatArray(3) { 0f } },
                message = "校准失败: ${e.message}"
            )
            _statusText.value = result.message
            onCalibrationComplete?.invoke(false, result)
            stopCalibration()
        }
    }

    /**
     * 计算硬铁偏移
     */
    private fun calculateHardIronOffset() {
        val minX = magneticReadings.minOf { it.first }
        val maxX = magneticReadings.maxOf { it.first }
        val minY = magneticReadings.minOf { it.second }
        val maxY = magneticReadings.maxOf { it.second }
        val minZ = magneticReadings.minOf { it.third }
        val maxZ = magneticReadings.maxOf { it.third }

        val offsetX = (minX + maxX) / 2
        val offsetY = (minY + maxY) / 2
        val offsetZ = (minZ + maxZ) / 2

        hardIronOffset = Triple(offsetX, offsetY, offsetZ)
        LogManager.d(TAG, "硬铁偏移: x=$offsetX, y=$offsetY, z=$offsetZ")
    }

    /**
     * 计算软铁校准矩阵
     */
    private fun calculateSoftIronMatrix() {
        for (i in 0..2) {
            for (j in 0..2) {
                softIronMatrix[i][j] = if (i == j) 1f else 0f
            }
        }

        val xValues = magneticReadings.map { it.first - hardIronOffset.first }
        val yValues = magneticReadings.map { it.second - hardIronOffset.second }
        val zValues = magneticReadings.map { it.third - hardIronOffset.third }

        val xRange = xValues.maxOf { abs(it) }
        val yRange = yValues.maxOf { abs(it) }
        val zRange = zValues.maxOf { abs(it) }

        val avgRange = (xRange + yRange + zRange) / 3

        if (xRange > 0) softIronMatrix[0][0] = avgRange / xRange
        if (yRange > 0) softIronMatrix[1][1] = avgRange / yRange
        if (zRange > 0) softIronMatrix[2][2] = avgRange / zRange

        LogManager.d(TAG, "软铁矩阵对角线: [${softIronMatrix[0][0]}, ${softIronMatrix[1][1]}, ${softIronMatrix[2][2]}]")
    }

    /**
     * 计算最终校准质量
     */
    private fun calculateFinalQuality(): Int {
        val calibratedReadings = magneticReadings.map { reading ->
            applyCalibration(reading)
        }

        val magnitudes = calibratedReadings.map { (x, y, z) ->
            sqrt(x * x + y * y + z * z)
        }

        val avgMagnitude = magnitudes.average()
        val variance = magnitudes.map { (it - avgMagnitude) * (it - avgMagnitude) }.average()
        val stdDev = sqrt(variance)

        // 根据标准差计算质量
        val quality = when {
            stdDev < 0.5 -> 100
            stdDev < 1.0 -> 95
            stdDev < 1.5 -> 90
            stdDev < 2.0 -> 85
            stdDev < 3.0 -> 80
            stdDev < 4.0 -> 75
            stdDev < 5.0 -> 70
            stdDev < 7.0 -> 65
            stdDev < 10.0 -> 60
            stdDev < 15.0 -> 50
            else -> 40
        }

        return quality.coerceIn(0, 100)
    }

    /**
     * 应用校准到磁场数据
     */
    fun applyCalibration(magneticField: Triple<Float, Float, Float>): Triple<Float, Float, Float> {
        val (rawX, rawY, rawZ) = magneticField

        val offsetX = rawX - hardIronOffset.first
        val offsetY = rawY - hardIronOffset.second
        val offsetZ = rawZ - hardIronOffset.third

        val calibratedX = softIronMatrix[0][0] * offsetX + softIronMatrix[0][1] * offsetY + softIronMatrix[0][2] * offsetZ
        val calibratedY = softIronMatrix[1][0] * offsetX + softIronMatrix[1][1] * offsetY + softIronMatrix[1][2] * offsetZ
        val calibratedZ = softIronMatrix[2][0] * offsetX + softIronMatrix[2][1] * offsetY + softIronMatrix[2][2] * offsetZ

        return Triple(calibratedX, calibratedY, calibratedZ)
    }

    /**
     * 应用校准到原始传感器数据
     */
    fun applyCalibrationToRaw(rawValues: FloatArray): FloatArray {
        val calibrated = applyCalibration(Triple(rawValues[0], rawValues[1], rawValues[2]))
        return floatArrayOf(calibrated.first, calibrated.second, calibrated.third)
    }

    /**
     * 获取当前状态描述
     */
    fun getCurrentStatus(): String {
        return when (_currentPhase.value) {
            is CalibrationPhase.WaitingForMotion -> "等待动作"
            is CalibrationPhase.DetectingFigure8 -> "检测中 ($figure8Count/3)"
            is CalibrationPhase.Calculating -> "计算中..."
            is CalibrationPhase.Completed -> "已完成"
            is CalibrationPhase.Failed -> "失败"
        }
    }

    companion object {
        private const val TAG = "CompassCalibrator"
    }
}
