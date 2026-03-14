package com.bh6aap.ic705Cter.util

import android.content.Context
import com.bh6aap.ic705Cter.data.database.entity.SatelliteEntity

/**
 * 卫星白名单管理器
 * 读取assets中的卫星白名单配置文件，提供过滤功能
 */
class SatelliteWhitelistManager(private val context: Context) {

    /**
     * 卫星信息数据类
     */
    data class WhitelistEntry(
        val noradId: String,
        val name: String,
        val type: SatelliteType
    )

    /**
     * 卫星类型
     */
    enum class SatelliteType {
        FM,      // FM卫星
        LINEAR,  // 线性卫星
        UNKNOWN  // 未知类型
    }

    // 白名单缓存
    private var whitelistCache: Map<String, WhitelistEntry>? = null

    /**
     * 加载白名单
     * @return 白名单映射表 (NORAD_ID -> WhitelistEntry)
     */
    fun loadWhitelist(): Map<String, WhitelistEntry> {
        // 如果已缓存，直接返回
        whitelistCache?.let { return it }

        val whitelist = mutableMapOf<String, WhitelistEntry>()

        try {
            context.assets.open("satellite_whitelist.txt").use { inputStream ->
                inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        // 跳过空行和注释行
                        val trimmedLine = line.trim()
                        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                            return@forEach
                        }

                        // 解析格式: NORAD_ID:卫星名称:类型
                        val parts = trimmedLine.split(":")
                        if (parts.size >= 3) {
                            val noradId = parts[0].trim()
                            val name = parts[1].trim()
                            val typeStr = parts[2].trim().uppercase()

                            val type = when (typeStr) {
                                "FM" -> SatelliteType.FM
                                "LINEAR" -> SatelliteType.LINEAR
                                else -> SatelliteType.UNKNOWN
                            }

                            whitelist[noradId] = WhitelistEntry(noradId, name, type)
                        }
                    }
                }
            }

            LogManager.i("SatelliteWhitelistManager", "【白名单】加载了 ${whitelist.size} 颗卫星")
        } catch (e: Exception) {
            LogManager.e("SatelliteWhitelistManager", "【白名单】加载失败", e)
        }

        whitelistCache = whitelist
        return whitelist
    }

    /**
     * 重新加载白名单（清除缓存）
     */
    fun reloadWhitelist(): Map<String, WhitelistEntry> {
        whitelistCache = null
        return loadWhitelist()
    }

    /**
     * 检查卫星是否在白名单中
     * @param noradId NORAD卫星编号
     * @return 如果在白名单中返回true
     */
    fun isInWhitelist(noradId: String): Boolean {
        val whitelist = loadWhitelist()
        return whitelist.containsKey(noradId)
    }

    /**
     * 获取卫星信息
     * @param noradId NORAD卫星编号
     * @return 卫星信息，如果不在白名单中返回null
     */
    fun getSatelliteInfo(noradId: String): WhitelistEntry? {
        val whitelist = loadWhitelist()
        return whitelist[noradId]
    }

    /**
     * 获取卫星类型
     * @param noradId NORAD卫星编号
     * @return 卫星类型
     */
    fun getSatelliteType(noradId: String): SatelliteType {
        return getSatelliteInfo(noradId)?.type ?: SatelliteType.UNKNOWN
    }

    /**
     * 过滤卫星列表，只返回白名单中的卫星
     * @param satellites 原始卫星列表
     * @return 过滤后的卫星列表
     */
    fun filterSatellites(satellites: List<SatelliteEntity>): List<SatelliteEntity> {
        val whitelist = loadWhitelist()
        return satellites.filter { satellite ->
            whitelist.containsKey(satellite.noradId)
        }
    }

    /**
     * 获取FM卫星列表
     * @param satellites 原始卫星列表
     * @return FM卫星列表
     */
    fun getFMSatellites(satellites: List<SatelliteEntity>): List<SatelliteEntity> {
        val whitelist = loadWhitelist()
        return satellites.filter { satellite ->
            whitelist[satellite.noradId]?.type == SatelliteType.FM
        }
    }

    /**
     * 获取线性卫星列表
     * @param satellites 原始卫星列表
     * @return 线性卫星列表
     */
    fun getLinearSatellites(satellites: List<SatelliteEntity>): List<SatelliteEntity> {
        val whitelist = loadWhitelist()
        return satellites.filter { satellite ->
            whitelist[satellite.noradId]?.type == SatelliteType.LINEAR
        }
    }

    /**
     * 获取所有白名单卫星的NORAD ID列表
     * @return NORAD ID列表
     */
    fun getAllWhitelistIds(): List<String> {
        return loadWhitelist().keys.toList()
    }

    /**
     * 获取白名单统计信息
     * @return 统计信息字符串
     */
    fun getStatistics(): String {
        val whitelist = loadWhitelist()
        val fmCount = whitelist.values.count { it.type == SatelliteType.FM }
        val linearCount = whitelist.values.count { it.type == SatelliteType.LINEAR }

        return "FM卫星: $fmCount 颗, 线性卫星: $linearCount 颗"
    }
}
