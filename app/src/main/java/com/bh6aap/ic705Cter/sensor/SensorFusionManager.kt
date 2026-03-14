package com.bh6aap.ic705Cter.sensor

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import com.bh6aap.ic705Cter.util.LogManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.round

/**
 * 传感器融合管理器（参考Look4Sat实现优化）
 * 使用旋转矢量传感器获取高精度姿态
 * 支持地磁偏角修正，输出真北方位角
 */
class SensorFusionManager(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "SensorFusionManager"
        private const val SENSOR_DELAY_MICROS = 16000 // 约60Hz，平衡精度和功耗
        private const val RAD2DEG = 57.29577951308232f // 180/π
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // 姿态数据流
    private val _orientation = MutableStateFlow(FusedOrientationData())
    val orientation: StateFlow<FusedOrientationData> = _orientation

    // 旋转矩阵和方向值
    private val rotationMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)

    // 传感器精度
    private var sensorAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE

    // 地磁偏角（根据GPS位置计算）
    private var magneticDeclination = 0f

    /**
     * 融合后的姿态数据
     */
    data class FusedOrientationData(
        val azimuth: Float = 0f,        // 方位角 0-360°，真北=0°（已修正地磁偏角）
        val pitch: Float = 0f,          // 俯仰角 -90°~90°，水平=0°
        val roll: Float = 0f,           // 横滚角 -180°~180°
        val magneticAzimuth: Float = 0f, // 磁北方位角（原始值）
        val isValid: Boolean = false,   // 数据是否有效
        val accuracy: Float = 0f,       // 精度估计 0-1
        val source: DataSource = DataSource.UNKNOWN,  // 数据来源
        val magneticDeclination: Float = 0f  // 地磁偏角
    )

    /**
     * 数据来源
     */
    enum class DataSource {
        UNKNOWN,
        ROTATION_VECTOR,      // 旋转矢量传感器（系统融合）
        FALLBACK              // 降级模式
    }

    /**
     * 开始传感器融合
     */
    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SENSOR_DELAY_MICROS)
            LogManager.i(TAG, "传感器融合已启动")
        } ?: run {
            LogManager.w(TAG, "设备不支持旋转矢量传感器")
        }
    }

    /**
     * 停止传感器融合
     */
    fun stop() {
        sensorManager.unregisterListener(this)
        LogManager.i(TAG, "传感器融合已停止")
    }

    /**
     * 更新地磁偏角（根据GPS位置）
     * 应在获取GPS位置后调用
     */
    fun updateMagneticDeclination(location: Location) {
        try {
            val geomagneticField = GeomagneticField(
                location.latitude.toFloat(),
                location.longitude.toFloat(),
                location.altitude.toFloat(),
                System.currentTimeMillis()
            )
            magneticDeclination = geomagneticField.declination
            LogManager.d(TAG, "地磁偏角更新: ${magneticDeclination}°")
        } catch (e: Exception) {
            LogManager.w(TAG, "计算地磁偏角失败", e)
        }
    }

    /**
     * 手动设置地磁偏角（用于校准）
     */
    fun setMagneticDeclination(declination: Float) {
        magneticDeclination = declination
        LogManager.i(TAG, "手动设置地磁偏角: ${declination}°")
    }

    /**
     * 获取当前地磁偏角
     */
    fun getMagneticDeclination(): Float = magneticDeclination

    override fun onSensorChanged(event: SensorEvent) {
        // 检查传感器精度，不可靠时不更新
        if (sensorAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            return
        }

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            updateOrientation(event.values)
        }
    }

    private fun updateOrientation(rotationVector: FloatArray) {
        // 从旋转矢量计算旋转矩阵
        SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)

        // 从旋转矩阵计算方向角
        SensorManager.getOrientation(rotationMatrix, orientationValues)

        // 转换为角度
        val azimuth = orientationValues[0] * RAD2DEG
        val pitch = orientationValues[1] * RAD2DEG
        val roll = orientationValues[2] * RAD2DEG

        // 磁北方位角（0-360°）
        val magneticAzimuth = (azimuth + 360f) % 360f

        // 应用地磁偏角修正，得到真北方位角
        val trueAzimuth = (magneticAzimuth + magneticDeclination + 360f) % 360f

        // 四舍五入到小数点后1位，减少抖动
        val roundedTrueAzimuth = round(trueAzimuth * 10) / 10
        val roundedPitch = round(pitch * 10) / 10
        val roundedRoll = round(roll * 10) / 10
        val roundedMagneticAzimuth = round(magneticAzimuth * 10) / 10

        _orientation.value = FusedOrientationData(
            azimuth = roundedTrueAzimuth,
            pitch = roundedPitch,
            roll = roundedRoll,
            magneticAzimuth = roundedMagneticAzimuth,
            isValid = true,
            accuracy = 0.9f,
            source = DataSource.ROTATION_VECTOR,
            magneticDeclination = magneticDeclination
        )
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        sensorAccuracy = accuracy
        LogManager.d(TAG, "传感器精度变化: $accuracy")
    }

    /**
     * 校准传感器
     */
    fun calibrate() {
        LogManager.i(TAG, "开始传感器校准")
        // 重置地磁偏角
        magneticDeclination = 0f
        LogManager.i(TAG, "传感器校准完成")
    }
}
