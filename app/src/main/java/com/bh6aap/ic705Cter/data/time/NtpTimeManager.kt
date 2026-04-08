package com.bh6aap.ic705Cter.data.time

import android.content.Context
import android.os.SystemClock
import com.bh6aap.ic705Cter.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Date

/**
 * NTP 时间同步管理类
 * 使用阿里云 NTP 服务器同步系统时间
 */
class NtpTimeManager(private val context: Context) {

    companion object {
        // 备用 NTP 服务器列表
        private val NTP_SERVERS = listOf(
            "ntp1.aliyun.com",
            "ntp2.aliyun.com",
            "time.google.com",
            "time.windows.com"
        )
        private const val NTP_PORT = 123
        private const val NTP_PACKET_SIZE = 48
        private const val NTP_MODE_CLIENT = 3
        private const val NTP_VERSION = 3
        private const val NTP_TIMEOUT_MS = 5000L // 5秒超时

        // 1970年1月1日到1900年1月1日的毫秒数
        private const val OFFSET_1900_TO_1970 = ((365L * 70L) + 17L) * 24L * 60L * 60L * 1000L
    }

    /**
     * NTP 同步结果
     */
    data class NtpResult(
        val success: Boolean,
        val ntpTime: Long? = null,
        val systemTime: Long = System.currentTimeMillis(),
        val elapsedRealtime: Long = SystemClock.elapsedRealtime(), // 同步时的elapsedRealtime
        val offset: Long = 0L,
        val roundTripDelay: Long = 0L,
        val errorMessage: String? = null
    )

    /**
     * 同步 NTP 时间（尝试多个服务器）
     * @return NtpResult 同步结果
     */
    suspend fun syncTime(): NtpResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()

        // 尝试多个 NTP 服务器
        for (server in NTP_SERVERS) {
            try {
                val result = withTimeoutOrNull(NTP_TIMEOUT_MS) {
                    requestNtpTime(server)
                }

                if (result != null && result.success) {
                    LogManager.i("NtpTimeManager", "NTP同步成功: $server")
                    return@withContext result
                } else if (result != null) {
                    errors.add("$server: ${result.errorMessage}")
                } else {
                    errors.add("$server: 超时")
                }
            } catch (e: Exception) {
                errors.add("$server: ${e.javaClass.simpleName} - ${e.message}")
            }
        }

        // 所有服务器都失败
        val errorMsg = "所有NTP服务器同步失败: ${errors.joinToString(", ")}"
        LogManager.e("NtpTimeManager", errorMsg)
        NtpResult(
            success = false,
            errorMessage = errorMsg
        )
    }

    /**
     * 请求 NTP 时间
     * @param server NTP服务器地址
     */
    private fun requestNtpTime(server: String): NtpResult {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket().apply {
                soTimeout = 3000 // 3秒超时
            }

            val address = try {
                InetAddress.getByName(server)
            } catch (e: Exception) {
                return NtpResult(
                    success = false,
                    errorMessage = "DNS解析失败: ${e.message}"
                )
            }

            // 构建 NTP 请求包
            val buffer = ByteArray(NTP_PACKET_SIZE).apply {
                // 设置第一个字节: LI(00) + VN(011) + Mode(011) = 0x1B
                this[0] = (NTP_MODE_CLIENT or (NTP_VERSION shl 3)).toByte()
            }

            val requestPacket = DatagramPacket(buffer, buffer.size, address, NTP_PORT)

            // 记录发送时间
            val requestTime = System.currentTimeMillis()
            val requestTicks = SystemClock.elapsedRealtime()

            // 发送请求
            socket.send(requestPacket)

            // 接收响应
            val responsePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(responsePacket)

            // 记录接收时间
            val responseTicks = SystemClock.elapsedRealtime()
            val responseTime = requestTime + (responseTicks - requestTicks)

            // 计算往返延迟
            val roundTripDelay = responseTicks - requestTicks

            // 解析 NTP 时间戳（从第32字节开始的64位时间戳）
            val ntpTime = parseNtpTime(buffer, 32)

            // 计算时间偏移
            // offset = ((t1 - t0) + (t2 - t3)) / 2
            // 简化计算: offset = ntpTime - systemTime + (roundTripDelay / 2)
            val offset = ntpTime - responseTime + (roundTripDelay / 2)

            NtpResult(
                success = true,
                ntpTime = ntpTime,
                systemTime = responseTime,
                elapsedRealtime = responseTicks, // 记录同步时的elapsedRealtime
                offset = offset,
                roundTripDelay = roundTripDelay
            )

        } catch (e: Exception) {
            NtpResult(
                success = false,
                errorMessage = "${e.javaClass.simpleName}: ${e.message}"
            )
        } finally {
            socket?.close()
        }
    }

    /**
     * 解析 NTP 时间戳
     * @param buffer 数据包
     * @param offset 时间戳起始位置
     * @return 毫秒时间戳
     */
    private fun parseNtpTime(buffer: ByteArray, offset: Int): Long {
        // 读取秒部分（32位）
        var seconds = 0L
        for (i in 0..3) {
            seconds = (seconds shl 8) or (buffer[offset + i].toLong() and 0xFFL)
        }

        // 读取小数部分（32位）
        var fraction = 0L
        for (i in 4..7) {
            fraction = (fraction shl 8) or (buffer[offset + i].toLong() and 0xFFL)
        }

        // 转换为毫秒
        // NTP 时间从 1900年1月1日 开始，需要减去偏移量得到 Unix 时间戳
        val milliseconds = (seconds * 1000L) + ((fraction * 1000L) / 0x100000000L)

        return milliseconds - OFFSET_1900_TO_1970
    }

    /**
     * 检查系统时间是否需要同步
     * @param thresholdMs 时间偏差阈值（毫秒）
     * @return 如果需要同步返回 true
     */
    suspend fun needSync(thresholdMs: Long = 5000L): Boolean {
        val result = syncTime()
        return result.success && kotlin.math.abs(result.offset) > thresholdMs
    }

    /**
     * 获取格式化的时间信息
     */
    fun formatTimeInfo(result: NtpResult): String {
        return if (result.success) {
            val ntpDate = result.ntpTime?.let { Date(it) }
            val systemDate = Date(result.systemTime)

            buildString {
                append("NTP 时间: ${ntpDate}\n")
                append("系统时间: ${systemDate}\n")
                append("时间偏移: ${result.offset} ms\n")
                append("网络延迟: ${result.roundTripDelay} ms")
            }
        } else {
            "时间同步失败: ${result.errorMessage}"
        }
    }

    /**
     * 获取当前精确时间（优先使用 NTP 校准后的时间）
     * 注意：Android 应用无法直接修改系统时间，需要 ROOT 权限
     * 这里返回校准后的时间戳供应用内部使用
     */
    suspend fun getAccurateTime(): Long {
        val result = syncTime()
        return if (result.success && result.ntpTime != null) {
            // 计算当前精确时间
            // NTP时间 + 从同步到现在经过的时间
            val elapsedSinceSync = SystemClock.elapsedRealtime() - result.elapsedRealtime
            result.ntpTime + elapsedSinceSync
        } else {
            System.currentTimeMillis()
        }
    }

    // 缓存的NTP结果
    private var cachedResult: NtpResult? = null
    private var lastSyncTime: Long = 0
    private var lastSyncAttemptTime: Long = 0
    private val CACHE_VALIDITY_MS = 5 * 60 * 1000L // 缓存5分钟
    private val SYNC_RETRY_INTERVAL_MS = 60 * 1000L // 同步失败重试间隔1分钟

    /**
     * 获取缓存的精确时间（如果缓存有效则使用缓存，否则重新同步）
     * 注意：即使NTP同步失败，如果有历史缓存仍使用缓存，避免时间基准突变
     */
    suspend fun getCachedAccurateTime(): Long = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()

        // 检查缓存是否有效（5分钟内）
        if (cachedResult?.success == true &&
            cachedResult?.ntpTime != null &&
            (currentTime - lastSyncTime) < CACHE_VALIDITY_MS) {
            // 使用缓存
            val elapsedSinceSync = SystemClock.elapsedRealtime() - cachedResult!!.elapsedRealtime
            cachedResult!!.ntpTime!! + elapsedSinceSync
        } else {
            // 缓存过期，检查是否需要尝试同步（避免频繁尝试）
            val shouldAttemptSync = (currentTime - lastSyncAttemptTime) >= SYNC_RETRY_INTERVAL_MS

            if (shouldAttemptSync) {
                lastSyncAttemptTime = currentTime
                // 尝试重新同步
                val result = syncTime()
                if (result.success && result.ntpTime != null) {
                    // 同步成功，更新缓存
                    cachedResult = result
                    lastSyncTime = currentTime
                    val elapsedSinceSync = SystemClock.elapsedRealtime() - result.elapsedRealtime
                    result.ntpTime + elapsedSinceSync
                } else if (cachedResult?.success == true && cachedResult?.ntpTime != null) {
                    // 同步失败，但有过期缓存，仍使用过期缓存（避免时间基准突变）
                    LogManager.w("NtpTimeManager", "NTP同步失败，使用过期缓存时间")
                    val elapsedSinceSync = SystemClock.elapsedRealtime() - cachedResult!!.elapsedRealtime
                    cachedResult!!.ntpTime!! + elapsedSinceSync
                } else {
                    // 无缓存且同步失败，回退到系统时间
                    LogManager.w("NtpTimeManager", "NTP同步失败且无缓存，使用系统时间")
                    System.currentTimeMillis()
                }
            } else {
                // 距离上次尝试同步不足1分钟，直接使用过期缓存（不打印日志）
                if (cachedResult?.success == true && cachedResult?.ntpTime != null) {
                    val elapsedSinceSync = SystemClock.elapsedRealtime() - cachedResult!!.elapsedRealtime
                    cachedResult!!.ntpTime!! + elapsedSinceSync
                } else {
                    System.currentTimeMillis()
                }
            }
        }
    }

    /**
     * 强制刷新NTP时间（忽略缓存）
     */
    suspend fun forceSyncTime(): Long {
        cachedResult = null
        lastSyncTime = 0
        return getCachedAccurateTime()
    }
}
