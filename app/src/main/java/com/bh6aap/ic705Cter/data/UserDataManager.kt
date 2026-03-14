package com.bh6aap.ic705Cter.data

import android.content.Context
import android.net.Uri
import com.bh6aap.ic705Cter.data.database.DatabaseHelper
import com.bh6aap.ic705Cter.data.database.entity.CwPresetEntity
import com.bh6aap.ic705Cter.data.database.entity.CustomTransmitterEntity
import com.bh6aap.ic705Cter.data.database.entity.FrequencyPresetEntity
import com.bh6aap.ic705Cter.data.database.entity.MemoEntity
import com.bh6aap.ic705Cter.data.database.entity.StationEntity
import com.bh6aap.ic705Cter.util.LogManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 用户数据管理器
 * 统一管理用户自定义数据的导出和导入
 * 包括：呼号、地面站、CW预设、频率预设、自定义转发器、备忘录、设置等
 */
class UserDataManager(private val context: Context) {

    companion object {
        private const val TAG = "UserDataManager"
        private const val EXPORT_VERSION = 1
        
        // 导出文件名格式
        fun generateExportFileName(): String {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            return "IC705_UserData_$timestamp.json"
        }
    }

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * 用户数据容器
     */
    data class UserDataExport(
        val version: Int = EXPORT_VERSION,
        val exportTime: Long = System.currentTimeMillis(),
        val exportTimeStr: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
        val callsign: String? = null,
        val stations: List<StationEntity> = emptyList(),
        val cwPresets: List<CwPresetEntity> = emptyList(),
        val frequencyPresets: List<FrequencyPresetEntity> = emptyList(),
        val customTransmitters: List<CustomTransmitterEntity> = emptyList(),
        val memos: List<MemoEntity> = emptyList(),
        val settings: Map<String, String> = emptyMap(),
        val customSatellites: List<CustomSatelliteData> = emptyList()
    )

    /**
     * 自定义卫星数据（简化版，用于导出）
     */
    data class CustomSatelliteData(
        val noradId: String,
        val name: String,
        val notes: String?,
        val isFavorite: Boolean
    )

    /**
     * 导出所有用户数据
     * @param uri 导出文件URI
     * @return 是否导出成功
     */
    suspend fun exportUserData(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            LogManager.i(TAG, "开始导出用户数据")

            // 收集所有用户数据
            val userData = collectUserData()

            // 转换为JSON
            val json = gson.toJson(userData)

            // 写入文件
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json)
                }
            } ?: throw Exception("无法打开输出流")

            val stats = buildExportStats(userData)
            LogManager.i(TAG, "用户数据导出成功: $stats")
            Result.success(stats)
        } catch (e: Exception) {
            LogManager.e(TAG, "导出用户数据失败", e)
            Result.failure(e)
        }
    }

    /**
     * 导入用户数据
     * @param uri 导入文件URI
     * @param mergeMode 是否合并模式（true=合并，false=覆盖）
     * @return 是否导入成功
     */
    suspend fun importUserData(uri: Uri, mergeMode: Boolean = true): Result<String> = withContext(Dispatchers.IO) {
        try {
            LogManager.i(TAG, "开始导入用户数据，合并模式: $mergeMode")

            // 读取JSON文件
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: throw Exception("无法打开输入流")

            // 解析JSON
            val userData = gson.fromJson(json, UserDataExport::class.java)
                ?: throw Exception("JSON解析失败")

            // 验证版本
            if (userData.version > EXPORT_VERSION) {
                throw Exception("不支持的导出文件版本: ${userData.version}，当前支持版本: $EXPORT_VERSION")
            }

            // 导入数据
            val stats = if (mergeMode) {
                mergeUserData(userData)
            } else {
                replaceUserData(userData)
            }

            LogManager.i(TAG, "用户数据导入成功: $stats")
            Result.success(stats)
        } catch (e: Exception) {
            LogManager.e(TAG, "导入用户数据失败", e)
            Result.failure(e)
        }
    }

    /**
     * 收集所有用户数据
     */
    private suspend fun collectUserData(): UserDataExport {
        // 获取呼号
        val callsign = dbHelper.getCallsign()

        // 获取地面站
        val stations = dbHelper.getAllStations().first()

        // 获取CW预设
        val cwPresets = dbHelper.getAllCwPresets().first()

        // 获取所有频率预设
        val frequencyPresets = mutableListOf<FrequencyPresetEntity>()
        dbHelper.getAllSatellites().first().forEach { satellite ->
            frequencyPresets.addAll(
                dbHelper.getFrequencyPresetsByNoradId(satellite.noradId).first()
            )
        }

        // 获取自定义转发器
        val customTransmitters = dbHelper.getAllCustomTransmitters().first()

        // 获取备忘录
        val memos = dbHelper.getAllMemos().first()

        // 获取所有设置
        val settings = dbHelper.getAllSettings().first()
            .filter { (key, _) ->
                // 只导出用户自定义设置，排除系统设置
                !key.startsWith("system_") && !key.startsWith("cache_")
            }

        // 获取收藏的卫星（作为自定义卫星）
        val favoriteSatellites = dbHelper.getFavoriteSatellites().first()
        val customSatellites = favoriteSatellites.map { sat ->
            CustomSatelliteData(
                noradId = sat.noradId,
                name = sat.name,
                notes = sat.notes,
                isFavorite = true
            )
        }

        return UserDataExport(
            callsign = callsign,
            stations = stations,
            cwPresets = cwPresets,
            frequencyPresets = frequencyPresets,
            customTransmitters = customTransmitters,
            memos = memos,
            settings = settings,
            customSatellites = customSatellites
        )
    }

    /**
     * 合并用户数据（保留现有数据，添加新数据）
     */
    private suspend fun mergeUserData(userData: UserDataExport): String {
        val stats = StringBuilder()

        // 导入呼号（如果不为空）
        userData.callsign?.let { newCallsign ->
            if (newCallsign.isNotBlank()) {
                val existingCallsign = dbHelper.getCallsign()
                if (existingCallsign.isNullOrBlank()) {
                    dbHelper.setCallsign(newCallsign)
                    stats.append("呼号: 导入 $newCallsign\n")
                } else {
                    stats.append("呼号: 已存在 $existingCallsign，跳过\n")
                }
            }
        }

        // 导入地面站
        var stationCount = 0
        userData.stations.forEach { station ->
            try {
                dbHelper.insertStation(station)
                stationCount++
            } catch (e: Exception) {
                // 可能已存在，忽略错误
            }
        }
        stats.append("地面站: 导入 $stationCount 个\n")

        // 导入CW预设
        var cwPresetCount = 0
        userData.cwPresets.forEach { preset ->
            try {
                dbHelper.insertCwPreset(preset)
                cwPresetCount++
            } catch (e: Exception) {
                dbHelper.updateCwPreset(preset)
                cwPresetCount++
            }
        }
        stats.append("CW预设: 导入/更新 $cwPresetCount 个\n")

        // 导入频率预设
        var freqPresetCount = 0
        userData.frequencyPresets.forEach { preset ->
            try {
                dbHelper.insertFrequencyPreset(preset)
                freqPresetCount++
            } catch (e: Exception) {
                dbHelper.updateFrequencyPreset(preset)
                freqPresetCount++
            }
        }
        stats.append("频率预设: 导入/更新 $freqPresetCount 个\n")

        // 导入自定义转发器
        var customTransCount = 0
        userData.customTransmitters.forEach { transmitter ->
            try {
                dbHelper.insertOrUpdateCustomTransmitter(transmitter)
                customTransCount++
            } catch (e: Exception) {
                LogManager.w(TAG, "导入自定义转发器失败: ${transmitter.uuid}", e)
            }
        }
        stats.append("自定义转发器: 导入 $customTransCount 个\n")

        // 导入备忘录
        var memoCount = 0
        userData.memos.forEach { memo ->
            try {
                dbHelper.insertMemo(memo)
                memoCount++
            } catch (e: Exception) {
                // 可能已存在
            }
        }
        stats.append("备忘录: 导入 $memoCount 个\n")

        // 导入设置（只导入不存在的）
        var settingCount = 0
        userData.settings.forEach { (key, value) ->
            val existing = dbHelper.getSetting(key)
            if (existing == null) {
                dbHelper.setSetting(key, value)
                settingCount++
            }
        }
        stats.append("设置: 导入 $settingCount 项\n")

        return stats.toString()
    }

    /**
     * 替换用户数据（清空现有数据，导入新数据）
     */
    private suspend fun replaceUserData(userData: UserDataExport): String {
        val stats = StringBuilder()

        // 清空并导入呼号
        userData.callsign?.let { newCallsign ->
            if (newCallsign.isNotBlank()) {
                dbHelper.setCallsign(newCallsign)
                stats.append("呼号: 设置为 $newCallsign\n")
            }
        }

        // 清空并导入地面站
        dbHelper.deleteAllStations()
        var stationCount = 0
        userData.stations.forEach { station ->
            dbHelper.insertStation(station)
            stationCount++
        }
        stats.append("地面站: 清空后导入 $stationCount 个\n")

        // 清空并导入CW预设
        dbHelper.deleteAllCwPresets()
        var cwPresetCount = 0
        userData.cwPresets.forEach { preset ->
            dbHelper.insertCwPreset(preset)
            cwPresetCount++
        }
        stats.append("CW预设: 清空后导入 $cwPresetCount 个\n")

        // 清空并导入频率预设
        dbHelper.getAllSatellites().first().forEach { satellite ->
            dbHelper.deleteAllFrequencyPresetsByNoradId(satellite.noradId)
        }
        var freqPresetCount = 0
        userData.frequencyPresets.forEach { preset ->
            dbHelper.insertFrequencyPreset(preset)
            freqPresetCount++
        }
        stats.append("频率预设: 清空后导入 $freqPresetCount 个\n")

        // 清空并导入自定义转发器
        // 注意：这里需要获取所有自定义转发器并删除
        var customTransCount = 0
        userData.customTransmitters.forEach { transmitter ->
            dbHelper.insertOrUpdateCustomTransmitter(transmitter)
            customTransCount++
        }
        stats.append("自定义转发器: 导入 $customTransCount 个\n")

        // 清空并导入备忘录
        dbHelper.deleteAllMemos()
        var memoCount = 0
        userData.memos.forEach { memo ->
            dbHelper.insertMemo(memo)
            memoCount++
        }
        stats.append("备忘录: 清空后导入 $memoCount 个\n")

        // 导入设置（覆盖模式）
        var settingCount = 0
        userData.settings.forEach { (key, value) ->
            dbHelper.setSetting(key, value)
            settingCount++
        }
        stats.append("设置: 覆盖导入 $settingCount 项\n")

        return stats.toString()
    }

    /**
     * 构建导出统计信息
     */
    private fun buildExportStats(userData: UserDataExport): String {
        val stats = StringBuilder()
        stats.append("呼号: ${userData.callsign ?: "未设置"}\n")
        stats.append("地面站: ${userData.stations.size} 个\n")
        stats.append("CW预设: ${userData.cwPresets.size} 个\n")
        stats.append("频率预设: ${userData.frequencyPresets.size} 个\n")
        stats.append("自定义转发器: ${userData.customTransmitters.size} 个\n")
        stats.append("备忘录: ${userData.memos.size} 个\n")
        stats.append("设置项: ${userData.settings.size} 项\n")
        stats.append("收藏卫星: ${userData.customSatellites.size} 个")
        return stats.toString()
    }

    /**
     * 验证导出文件格式
     */
    suspend fun validateExportFile(uri: Uri): Result<UserDataExport> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: throw Exception("无法打开输入流")

            val userData = gson.fromJson(json, UserDataExport::class.java)
                ?: throw Exception("JSON解析失败")

            if (userData.version > EXPORT_VERSION) {
                throw Exception("不支持的导出文件版本: ${userData.version}")
            }

            Result.success(userData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * DatabaseHelper扩展函数
 */
suspend fun DatabaseHelper.setCallsign(callsign: String) {
    // 使用已有的saveCallsign方法
    saveCallsign(callsign)
}

suspend fun DatabaseHelper.setSetting(key: String, value: String) {
    // 使用已有的saveSetting方法
    saveSetting(key, value, null)
}
