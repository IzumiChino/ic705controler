package com.bh6aap.ic705Cter.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * GPS 位置管理类
 * 提供高精度位置获取功能
 */
class GpsManager(private val context: Context) {

    private val locationManager: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * 检查是否有位置权限
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查 GPS 是否可用
     */
    fun isGpsEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    /**
     * 检查网络定位是否可用
     */
    fun isNetworkLocationEnabled(): Boolean {
        return locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * 获取最后已知位置（最快但可能不准确）
     */
    fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null

        var bestLocation: Location? = null

        // 尝试获取 GPS 最后位置
        try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let {
                if (isBetterLocation(it, bestLocation)) {
                    bestLocation = it
                }
            }
        } catch (e: SecurityException) {
            // 权限问题
        }

        // 尝试获取网络最后位置
        try {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let {
                if (isBetterLocation(it, bestLocation)) {
                    bestLocation = it
                }
            }
        } catch (e: SecurityException) {
            // 权限问题
        }

        // 尝试获取融合定位最后位置（Android 12+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER)?.let {
                    if (isBetterLocation(it, bestLocation)) {
                        bestLocation = it
                    }
                }
            } catch (e: SecurityException) {
                // 权限问题
            }
        }

        return bestLocation
    }

    /**
     * 获取当前位置（挂起函数）
     * @param timeoutMs 超时时间（毫秒）
     * @return 位置信息，超时或失败返回 null
     */
    suspend fun getCurrentLocation(
        timeoutMs: Long = 30000
    ): Location? = withContext(Dispatchers.Main) {
        if (!hasLocationPermission()) return@withContext null

        var bestLocation: Location? = null

        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val locationCallback = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        // 记录每次位置更新
                        android.util.Log.d("GpsManager", "位置更新: 精度=${location.accuracy}米, 提供者=${location.provider}")

                        // 保存最佳位置
                        if (bestLocation == null || location.accuracy < bestLocation!!.accuracy) {
                            bestLocation = location
                        }

                        // 获取到第一个位置后就返回，不等待更高精度
                        locationManager.removeUpdates(this)
                        continuation.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

                    override fun onProviderEnabled(provider: String) {}

                    override fun onProviderDisabled(provider: String) {
                        locationManager.removeUpdates(this)
                        continuation.resume(bestLocation)
                    }
                }

                try {
                    // 优先请求 GPS 高精度定位
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000, // 1秒更新间隔
                        0f,   // 最小距离变化
                        locationCallback,
                        Looper.getMainLooper()
                    )

                    // 同时请求网络定位作为备用
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        1000,
                        0f,
                        locationCallback,
                        Looper.getMainLooper()
                    )

                } catch (e: SecurityException) {
                    continuation.resume(bestLocation)
                }

                continuation.invokeOnCancellation {
                    locationManager.removeUpdates(locationCallback)
                }
            }
        } ?: bestLocation ?: getLastKnownLocation()
    }

    /**
     * 获取当前精确位置（Android 12+ 使用新 API）
     * @param timeoutMs 超时时间（毫秒）
     * @return 位置信息
     */
    suspend fun getCurrentLocationModern(timeoutMs: Long = 30000): Location? =
        withContext(Dispatchers.IO) {
            if (!hasLocationPermission()) return@withContext null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val cancellationSignal = CancellationSignal()
                    val location = withTimeoutOrNull(timeoutMs) {
                        suspendCancellableCoroutine { continuation ->
                            locationManager.getCurrentLocation(
                                LocationManager.GPS_PROVIDER,
                                cancellationSignal,
                                context.mainExecutor
                            ) { location ->
                                continuation.resume(location)
                            }
                        }
                    }
                    location ?: getLastKnownLocation()
                } catch (e: SecurityException) {
                    getLastKnownLocation()
                }
            } else {
                getCurrentLocation(timeoutMs)
            }
        }

    /**
     * 获取网络定位位置（GSM基站定位）
     * 作为GPS失败的备用方案
     * @param timeoutMs 超时时间（毫秒）
     * @return 位置信息
     */
    suspend fun getNetworkLocation(timeoutMs: Long = 10000): Location? =
        withContext(Dispatchers.IO) {
            if (!hasLocationPermission()) return@withContext null

            var bestLocation: Location? = null

            val location = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { continuation ->
                    val locationCallback = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            android.util.Log.d("GpsManager", "网络位置更新: 精度=${location.accuracy}米, 提供者=${location.provider}")

                            // 保存最佳位置
                            if (bestLocation == null || location.accuracy < bestLocation!!.accuracy) {
                                bestLocation = location
                            }

                            // 获取到位置后立即返回
                            locationManager.removeUpdates(this)
                            continuation.resume(location)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

                        override fun onProviderEnabled(provider: String) {}

                        override fun onProviderDisabled(provider: String) {
                            locationManager.removeUpdates(this)
                            continuation.resume(bestLocation)
                        }
                    }

                    try {
                        // 请求网络定位（基于GSM基站或WiFi）
                        locationManager.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            1000, // 1秒更新间隔
                            0f,   // 最小距离变化
                            locationCallback,
                            Looper.getMainLooper()
                        )
                    } catch (e: SecurityException) {
                        continuation.resume(bestLocation)
                    } catch (e: IllegalArgumentException) {
                        // 网络定位不可用
                        continuation.resume(bestLocation)
                    }

                    continuation.invokeOnCancellation {
                        locationManager.removeUpdates(locationCallback)
                    }
                }
            } ?: bestLocation ?: getLastKnownLocation()

            // 网络定位通常没有海拔信息，设置默认值为0
            location?.let {
                if (!it.hasAltitude()) {
                    it.altitude = 0.0
                    android.util.Log.d("GpsManager", "网络定位无海拔信息，设置默认值: 0m")
                }
            }

            location
        }

    /**
     * 持续监听位置变化（Flow）
     */
    fun locationFlow(): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("No location permission"))
            return@callbackFlow
        }

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

            override fun onProviderEnabled(provider: String) {}

            override fun onProviderDisabled(provider: String) {}
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000, // 5秒
                10f,  // 10米
                locationListener,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            close(e)
        }

        awaitClose {
            locationManager.removeUpdates(locationListener)
        }
    }

    /**
     * 比较两个位置，返回更好的那个
     */
    private fun isBetterLocation(newLocation: Location, currentBestLocation: Location?): Boolean {
        if (currentBestLocation == null) return true

        // 检查时间戳
        val timeDelta = newLocation.time - currentBestLocation.time
        val isSignificantlyNewer = timeDelta > 60000 // 1分钟
        val isSignificantlyOlder = timeDelta < -60000
        val isNewer = timeDelta > 0

        if (isSignificantlyNewer) return true
        if (isSignificantlyOlder) return false

        // 检查精度
        val accuracyDelta = (newLocation.accuracy - currentBestLocation.accuracy).toInt()
        val isLessAccurate = accuracyDelta > 0
        val isMoreAccurate = accuracyDelta < 0
        val isSignificantlyLessAccurate = accuracyDelta > 200

        // 检查提供者
        val isFromSameProvider = newLocation.provider == currentBestLocation.provider

        // 优先选择更精确的
        if (isMoreAccurate) return true
        if (isNewer && !isLessAccurate) return true
        if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) return true

        return false
    }

    /**
     * 获取位置信息描述
     */
    fun getLocationInfo(location: Location?): String {
        if (location == null) return "位置不可用"

        return buildString {
            append("纬度: ${location.latitude}\n")
            append("经度: ${location.longitude}\n")
            append("海拔: ${location.altitude} 米\n")
            append("精度: ${location.accuracy} 米\n")
            append("提供者: ${location.provider}\n")
            if (location.hasSpeed()) {
                append("速度: ${location.speed} m/s\n")
            }
            if (location.hasBearing()) {
                append("方向: ${location.bearing}°\n")
            }
        }
    }
}

/**
 * 位置数据数据类
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float,
    val provider: String,
    val timestamp: Long
) {
    companion object {
        fun fromLocation(location: Location): LocationData {
            return LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                accuracy = location.accuracy,
                provider = location.provider ?: "unknown",
                timestamp = location.time
            )
        }
    }
}
