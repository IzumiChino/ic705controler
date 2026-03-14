package com.bh6aap.ic705Cter.data.api

import android.content.Context
import com.bh6aap.ic705Cter.data.database.DatabaseHelper
import com.bh6aap.ic705Cter.data.database.entity.SyncRecordEntity
import com.bh6aap.ic705Cter.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 卫星详细信息数据管理类
 * 从 SatNOGS 数据库获取卫星详细信息（包含别名）
 */
class SatelliteDataManager(private val context: Context) {

    companion object {
        private const val SATNOGS_API_BASE_URL = "https://db.satnogs.org/api/"
        private const val REQUEST_TIMEOUT_SECONDS = 30L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val dbHelper = DatabaseHelper.getInstance(context)

    /**
     * 检查是否需要同步卫星信息数据
     * @param maxAgeHours 数据最大有效时间（小时），默认 168 小时（7天）
     * @return 如果需要同步返回 true
     */
    suspend fun needSync(maxAgeHours: Int = 168): Boolean = withContext(Dispatchers.IO) {
        val infoCount = dbHelper.getSatelliteInfoCount()
        if (infoCount == 0) {
            return@withContext true
        }

        val lastSync = dbHelper.getLastSyncRecord("satellite_info_satnogs")
        if (lastSync == null) {
            return@withContext true
        }

        val maxAgeMillis = maxAgeHours * 60 * 60 * 1000L
        val isExpired = (System.currentTimeMillis() - lastSync.syncTime) > maxAgeMillis

        isExpired
    }

    /**
     * 获取卫星详细信息
     * @param forceRefresh 强制刷新，忽略缓存
     * @return 是否成功
     */
    suspend fun fetchSatelliteInfo(forceRefresh: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            // 如果不是强制刷新，检查是否需要同步
            if (!forceRefresh && !needSync()) {
                LogManager.i("SatelliteDataManager", "【卫星信息】数据仍在有效期内，跳过同步")
                return@withContext true
            }

            val apiUrl = "${SATNOGS_API_BASE_URL}satellites/"

            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }

                val responseBody = response.body?.string()
                    ?: throw IOException("Empty response")

                // 解析 JSON 数据
                val satelliteInfos = parseSatelliteInfo(responseBody)

                if (satelliteInfos.isNotEmpty()) {
                    // 清空旧数据
                    dbHelper.deleteAllSatelliteInfos()

                    // 插入新数据
                    dbHelper.insertSatelliteInfos(satelliteInfos)

                    // 记录同步时间
                    val syncRecord = SyncRecordEntity(
                        syncType = "satellite_info_satnogs",
                        syncTime = System.currentTimeMillis(),
                        recordCount = satelliteInfos.size,
                        source = SATNOGS_API_BASE_URL
                    )
                    dbHelper.insertSyncRecord(syncRecord)

                    LogManager.i("SatelliteDataManager", "【卫星信息】同步完成，共 ${satelliteInfos.size} 颗卫星")
                }
            }

            return@withContext true
        } catch (e: Exception) {
            LogManager.e("SatelliteDataManager", "【卫星信息】同步失败: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * 获取特定卫星的详细信息
     * @param noradId NORAD 编号
     * @return 卫星详细信息
     */
    suspend fun fetchSatelliteInfoByNoradId(noradId: Int): SatelliteInfo? = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "${SATNOGS_API_BASE_URL}satellites/?norad_cat_id=$noradId"

            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }

                val responseBody = response.body?.string()
                    ?: throw IOException("Empty response")

                val satelliteInfos = parseSatelliteInfo(responseBody)
                satelliteInfos.firstOrNull()
            }
        } catch (e: Exception) {
            LogManager.e("SatelliteDataManager", "【卫星信息】获取单颗卫星失败: $noradId", e)
            null
        }
    }

    /**
     * 解析卫星信息 JSON 数据
     */
    private fun parseSatelliteInfo(jsonString: String): List<SatelliteInfo> {
        val satelliteInfos = mutableListOf<SatelliteInfo>()

        try {
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                val satelliteInfo = SatelliteInfo(
                    satId = obj.optString("sat_id", ""),
                    noradCatId = obj.optInt("norad_cat_id", 0),
                    noradFollowId = if (obj.isNull("norad_follow_id")) null else obj.optInt("norad_follow_id"),
                    name = obj.optString("name", "Unknown"),
                    names = if (obj.isNull("names")) null else obj.optString("names"),
                    image = if (obj.isNull("image")) null else obj.optString("image"),
                    status = obj.optString("status", ""),
                    decayed = if (obj.isNull("decayed")) null else obj.optString("decayed"),
                    launched = if (obj.isNull("launched")) null else obj.optString("launched"),
                    deployed = if (obj.isNull("deployed")) null else obj.optString("deployed"),
                    website = if (obj.isNull("website")) null else obj.optString("website"),
                    operator = if (obj.isNull("operator")) null else obj.optString("operator"),
                    countries = if (obj.isNull("countries")) null else obj.optString("countries"),
                    telemetries = null, // 暂不解析复杂数组
                    updated = obj.optString("updated", ""),
                    citation = if (obj.isNull("citation")) null else obj.optString("citation"),
                    isFrequencyViolator = obj.optBoolean("is_frequency_violator", false),
                    associatedSatellites = null // 暂不解析复杂数组
                )

                // 只添加有效的卫星信息
                if (satelliteInfo.satId.isNotBlank() && satelliteInfo.noradCatId > 0) {
                    satelliteInfos.add(satelliteInfo)
                }
            }
        } catch (e: Exception) {
            LogManager.e("SatelliteDataManager", "【卫星信息】解析失败", e)
        }

        return satelliteInfos
    }

    /**
     * 获取本地存储的卫星信息数量
     */
    suspend fun getLocalSatelliteInfoCount(): Int = withContext(Dispatchers.IO) {
        dbHelper.getSatelliteInfoCount()
    }

    /**
     * 检查本地是否有卫星信息数据
     */
    suspend fun hasLocalData(): Boolean = withContext(Dispatchers.IO) {
        dbHelper.hasSatelliteInfoData()
    }
}
