package com.bh6aap.ic705Cter.util

import android.content.Context
import android.content.SharedPreferences
import com.bh6aap.ic705Cter.data.database.entity.SatelliteEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * 可编辑的卫星白名单管理器
 * 支持手动添加、删除、编辑卫星白名单
 */
class EditableWhitelistManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    companion object {
        private const val PREF_NAME = "editable_whitelist_prefs"
        private const val KEY_WHITELIST = "whitelist_entries"
        private const val KEY_DEFAULT_WHITELIST_LOADED = "default_whitelist_loaded"

        @Volatile
        private var instance: EditableWhitelistManager? = null

        fun getInstance(context: Context): EditableWhitelistManager {
            return instance ?: synchronized(this) {
                instance ?: EditableWhitelistManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * 卫星条目数据类
     */
    data class WhitelistEntry(
        val noradId: String,
        val name: String,
        val type: SatelliteType,
        val isCustom: Boolean = true  // 是否为自定义添加
    ) {
        fun toJson(): JSONObject {
            return JSONObject().apply {
                put("noradId", noradId)
                put("name", name)
                put("type", type.name)
                put("isCustom", isCustom)
            }
        }

        companion object {
            fun fromJson(json: JSONObject): WhitelistEntry {
                return WhitelistEntry(
                    noradId = json.getString("noradId"),
                    name = json.getString("name"),
                    type = SatelliteType.valueOf(json.getString("type")),
                    isCustom = json.optBoolean("isCustom", true)
                )
            }
        }
    }

    /**
     * 卫星类型
     */
    enum class SatelliteType {
        FM,      // FM卫星
        LINEAR,  // 线性卫星
        UNKNOWN  // 未知类型
    }

    /**
     * 初始化默认白名单（从assets加载）
     */
    suspend fun initDefaultWhitelistIfNeeded() = mutex.withLock {
        val loaded = prefs.getBoolean(KEY_DEFAULT_WHITELIST_LOADED, false)
        if (!loaded) {
            // 从assets加载默认白名单
            val defaultEntries = loadDefaultWhitelistFromAssets()
            // 保存到SharedPreferences
            saveEntriesInternal(defaultEntries)
            prefs.edit().putBoolean(KEY_DEFAULT_WHITELIST_LOADED, true).apply()
            LogManager.i("EditableWhitelistManager", "【白名单】已初始化默认白名单，共 ${defaultEntries.size} 颗卫星")
        }
    }

    /**
     * 从assets加载默认白名单
     */
    private fun loadDefaultWhitelistFromAssets(): List<WhitelistEntry> {
        val entries = mutableListOf<WhitelistEntry>()
        try {
            context.assets.open("satellite_whitelist.txt").use { inputStream ->
                inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val trimmedLine = line.trim()
                        if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                            return@forEach
                        }

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

                            entries.add(WhitelistEntry(noradId, name, type, false))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogManager.e("EditableWhitelistManager", "【白名单】加载默认白名单失败", e)
        }
        return entries
    }

    /**
     * 获取所有白名单条目
     */
    suspend fun getAllEntries(): List<WhitelistEntry> = mutex.withLock {
        return loadEntriesInternal()
    }

    /**
     * 添加卫星到白名单
     * @param noradId NORAD卫星编号
     * @param name 卫星名称
     * @param type 卫星类型
     * @return 是否添加成功
     */
    suspend fun addSatellite(noradId: String, name: String, type: SatelliteType): Boolean = mutex.withLock {
        val entries = loadEntriesInternal().toMutableList()

        // 检查是否已存在
        if (entries.any { it.noradId == noradId }) {
            LogManager.w("EditableWhitelistManager", "【白名单】卫星 $noradId 已存在")
            return@withLock false
        }

        // 添加新条目
        val newEntry = WhitelistEntry(noradId.trim(), name.trim(), type, true)
        entries.add(newEntry)
        saveEntriesInternal(entries)

        LogManager.i("EditableWhitelistManager", "【白名单】已添加卫星: $name (NORAD: $noradId)")
        return@withLock true
    }

    /**
     * 更新卫星信息
     * @param noradId NORAD卫星编号
     * @param newName 新名称（null表示不修改）
     * @param newType 新类型（null表示不修改）
     * @return 是否更新成功
     */
    suspend fun updateSatellite(
        noradId: String,
        newName: String? = null,
        newType: SatelliteType? = null
    ): Boolean = mutex.withLock {
        val entries = loadEntriesInternal().toMutableList()
        val index = entries.indexOfFirst { it.noradId == noradId }

        if (index == -1) {
            LogManager.w("EditableWhitelistManager", "【白名单】卫星 $noradId 不存在")
            return@withLock false
        }

        val oldEntry = entries[index]
        val updatedEntry = oldEntry.copy(
            name = newName?.trim() ?: oldEntry.name,
            type = newType ?: oldEntry.type,
            isCustom = true
        )

        entries[index] = updatedEntry
        saveEntriesInternal(entries)

        LogManager.i("EditableWhitelistManager", "【白名单】已更新卫星: ${updatedEntry.name} (NORAD: $noradId)")
        return@withLock true
    }

    /**
     * 从白名单删除卫星
     * @param noradId NORAD卫星编号
     * @return 是否删除成功
     */
    suspend fun removeSatellite(noradId: String): Boolean = mutex.withLock {
        val entries = loadEntriesInternal().toMutableList()
        val removed = entries.removeIf { it.noradId == noradId }

        if (removed) {
            saveEntriesInternal(entries)
            LogManager.i("EditableWhitelistManager", "【白名单】已删除卫星: $noradId")
        } else {
            LogManager.w("EditableWhitelistManager", "【白名单】卫星 $noradId 不存在，无法删除")
        }

        return@withLock removed
    }

    /**
     * 检查卫星是否在白名单中
     */
    suspend fun isInWhitelist(noradId: String): Boolean = mutex.withLock {
        return loadEntriesInternal().any { it.noradId == noradId }
    }

    /**
     * 获取卫星信息
     */
    suspend fun getSatelliteInfo(noradId: String): WhitelistEntry? = mutex.withLock {
        return loadEntriesInternal().find { it.noradId == noradId }
    }

    /**
     * 过滤卫星列表
     */
    suspend fun filterSatellites(satellites: List<SatelliteEntity>): List<SatelliteEntity> = mutex.withLock {
        val whitelistIds = loadEntriesInternal().map { it.noradId }.toSet()
        return satellites.filter { it.noradId in whitelistIds }
    }

    /**
     * 重置为默认白名单
     */
    suspend fun resetToDefault() = mutex.withLock {
        val defaultEntries = loadDefaultWhitelistFromAssets()
        saveEntriesInternal(defaultEntries)
        LogManager.i("EditableWhitelistManager", "【白名单】已重置为默认白名单")
    }

    /**
     * 获取统计信息
     */
    suspend fun getStatistics(): String = mutex.withLock {
        val entries = loadEntriesInternal()
        val fmCount = entries.count { it.type == SatelliteType.FM }
        val linearCount = entries.count { it.type == SatelliteType.LINEAR }
        val customCount = entries.count { it.isCustom }
        return "共 ${entries.size} 颗 (FM: $fmCount, 线性: $linearCount, 自定义: $customCount)"
    }

    /**
     * 从SharedPreferences加载条目
     */
    private fun loadEntriesInternal(): List<WhitelistEntry> {
        val jsonStr = prefs.getString(KEY_WHITELIST, null)
        if (jsonStr.isNullOrEmpty()) {
            return emptyList()
        }

        return try {
            val jsonArray = JSONArray(jsonStr)
            val entries = mutableListOf<WhitelistEntry>()
            for (i in 0 until jsonArray.length()) {
                val jsonObj = jsonArray.getJSONObject(i)
                entries.add(WhitelistEntry.fromJson(jsonObj))
            }
            entries
        } catch (e: Exception) {
            LogManager.e("EditableWhitelistManager", "【白名单】解析失败", e)
            emptyList()
        }
    }

    /**
     * 保存条目到SharedPreferences
     */
    private fun saveEntriesInternal(entries: List<WhitelistEntry>) {
        val jsonArray = JSONArray()
        entries.forEach { entry ->
            jsonArray.put(entry.toJson())
        }
        prefs.edit().putString(KEY_WHITELIST, jsonArray.toString()).apply()
    }
}
