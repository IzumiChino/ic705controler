package com.bh6aap.ic705Cter.util

import com.bh6aap.ic705Cter.data.database.entity.SatelliteEntity
import org.orekit.frames.FramesFactory
import org.orekit.propagation.analytical.tle.TLE
import org.orekit.propagation.analytical.tle.TLEPropagator
import org.orekit.time.AbsoluteDate
import org.orekit.time.TimeScalesFactory
import org.orekit.utils.Constants
import org.orekit.utils.IERSConventions
import org.orekit.bodies.GeodeticPoint
import org.orekit.bodies.OneAxisEllipsoid
import org.orekit.frames.TopocentricFrame
import java.util.Date

/**
 * 卫星计算器
 * 使用Orekit计算卫星的位置和相关信息
 */
object SatelliteCalculator {

    /**
     * 卫星位置信息
     */
    data class SatellitePosition(
        val latitude: Double,      // 纬度（度）
        val longitude: Double,     // 经度（度）
        val altitude: Double,      // 高度（km）
        val velocity: Double,      // 速度（km/s）
        val azimuth: Double,       // 方位角（度）
        val elevation: Double,     // 仰角（度）
        val range: Double,         // 距离（km）
        val nextPassTime: Date?    // 下次过境时间
    )

    /**
     * 计算卫星当前位置
     * @param satellite 卫星实体
     * @param observerLat 观测者纬度（度）
     * @param observerLon 观测者经度（度）
     * @param observerAlt 观测者高度（米）
     * @return 卫星位置信息
     */
    fun calculatePosition(
        satellite: SatelliteEntity,
        observerLat: Double,
        observerLon: Double,
        observerAlt: Double = 0.0
    ): SatellitePosition? {
        return try {
            // 创建TLE对象
            val tle = TLE(satellite.tleLine1, satellite.tleLine2)

            // 创建TLE传播器
            val propagator = TLEPropagator.selectExtrapolator(tle)

            // 获取当前时间
            val currentDate = AbsoluteDate(Date(), TimeScalesFactory.getUTC())

            // 获取卫星状态
            val state = propagator.propagate(currentDate)

            // 获取地球参考系
            val earth = OneAxisEllipsoid(
                Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                Constants.WGS84_EARTH_FLATTENING,
                FramesFactory.getITRF(IERSConventions.IERS_2010, true)
            )

            // 获取卫星在地心地固坐标系中的位置
            val position = state.position
            val velocity = state.velocity

            // 转换为地理坐标
            val geodeticPoint = earth.transform(
                position,
                FramesFactory.getITRF(IERSConventions.IERS_2010, true),
                currentDate
            )

            // 创建观测者地面站
            val observerPoint = GeodeticPoint(
                Math.toRadians(observerLat),
                Math.toRadians(observerLon),
                observerAlt
            )
            val observerFrame = TopocentricFrame(earth, observerPoint, "Observer")

            // 计算观测参数
            val azimuth = Math.toDegrees(observerFrame.getAzimuth(position, FramesFactory.getITRF(IERSConventions.IERS_2010, true), currentDate))
            val elevation = Math.toDegrees(observerFrame.getElevation(position, FramesFactory.getITRF(IERSConventions.IERS_2010, true), currentDate))
            val range = observerFrame.getRange(position, FramesFactory.getITRF(IERSConventions.IERS_2010, true), currentDate)

            SatellitePosition(
                latitude = Math.toDegrees(geodeticPoint.latitude),
                longitude = Math.toDegrees(geodeticPoint.longitude),
                altitude = geodeticPoint.altitude / 1000.0, // 转换为km
                velocity = velocity.norm / 1000.0, // 转换为km/s
                azimuth = azimuth,
                elevation = elevation,
                range = range / 1000.0, // 转换为km
                nextPassTime = null // 简化版本，不计算下次过境
            )
        } catch (e: Exception) {
            LogManager.e("SatelliteCalculator", "计算卫星位置失败", e)
            null
        }
    }

    /**
     * 计算卫星轨道周期（分钟）
     */
    fun calculateOrbitalPeriod(satellite: SatelliteEntity): Double? {
        return try {
            val tle = TLE(satellite.tleLine1, satellite.tleLine2)
            val propagator = TLEPropagator.selectExtrapolator(tle)
            val state = propagator.propagate(AbsoluteDate(Date(), TimeScalesFactory.getUTC()))
            val orbit = state.orbit
            val period = orbit.keplerianPeriod
            period / 60.0 // 转换为分钟
        } catch (e: Exception) {
            LogManager.e("SatelliteCalculator", "计算轨道周期失败", e)
            null
        }
    }

    /**
     * 获取卫星高度类别
     */
    fun getAltitudeCategory(altitudeKm: Double): String {
        return when {
            altitudeKm < 2000 -> "低轨道 (LEO)"
            altitudeKm < 35786 -> "中轨道 (MEO)"
            altitudeKm < 40000 -> "地球同步轨道 (GEO)"
            else -> "高轨道 (HEO)"
        }
    }
}
