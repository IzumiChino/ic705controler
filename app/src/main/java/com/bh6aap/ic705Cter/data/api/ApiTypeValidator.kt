package com.bh6aap.ic705Cter.data.api

import com.bh6aap.ic705Cter.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * API类型验证器
 * 用于检测用户提供的API链接是否返回正确的数据类型
 */
object ApiTypeValidator {

    private const val TAG = "ApiTypeValidator"

    // API类型枚举
    enum class ApiType {
        SATELLITE,      // 卫星数据API
        TRANSMITTER,    // 转发器数据API
        TLE,            // TLE数据API
        UNKNOWN,        // 未知类型
        INVALID         // 无效/无法访问
    }

    // 验证结果
    data class ValidationResult(
        val isValid: Boolean,
        val apiType: ApiType,
        val message: String,
        val sampleData: String? = null
    )

    // HTTP客户端
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 验证API链接
     * @param url API链接
     * @param expectedType 期望的API类型（可选）
     * @return 验证结果
     */
    suspend fun validateApi(
        url: String,
        expectedType: ApiType? = null
    ): ValidationResult = withContext(Dispatchers.IO) {
        try {
            LogManager.i(TAG, "开始验证API: $url")

            // 检查URL格式
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                return@withContext ValidationResult(
                    isValid = false,
                    apiType = ApiType.INVALID,
                    message = "URL格式错误，必须以http://或https://开头"
                )
            }

            // 发送请求
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext ValidationResult(
                    isValid = false,
                    apiType = ApiType.INVALID,
                    message = "HTTP错误: ${response.code} - ${response.message}"
                )
            }

            val body = response.body?.string()
                ?: return@withContext ValidationResult(
                    isValid = false,
                    apiType = ApiType.INVALID,
                    message = "API返回空数据"
                )

            // 尝试解析API类型 (支持JSON和纯文本格式)
            val detectedType = detectApiType(body, url)

            // 如果指定了期望类型，检查是否匹配
            if (expectedType != null && detectedType != expectedType) {
                return@withContext ValidationResult(
                    isValid = false,
                    apiType = detectedType,
                    message = "API类型不匹配。期望: ${getTypeName(expectedType)}, 实际: ${getTypeName(detectedType)}",
                    sampleData = body.take(200)
                )
            }

            // 检查是否有效类型
            if (detectedType == ApiType.UNKNOWN) {
                return@withContext ValidationResult(
                    isValid = false,
                    apiType = ApiType.UNKNOWN,
                    message = "无法识别API数据类型，请检查API格式",
                    sampleData = body.take(200)
                )
            }

            if (detectedType == ApiType.INVALID) {
                return@withContext ValidationResult(
                    isValid = false,
                    apiType = ApiType.INVALID,
                    message = "API返回的数据格式无效",
                    sampleData = body.take(200)
                )
            }

            // 验证成功
            return@withContext ValidationResult(
                isValid = true,
                apiType = detectedType,
                message = "API验证成功: ${getTypeName(detectedType)}",
                sampleData = body.take(200)
            )

        } catch (e: IOException) {
            LogManager.e(TAG, "API验证网络错误", e)
            return@withContext ValidationResult(
                isValid = false,
                apiType = ApiType.INVALID,
                message = "网络错误: ${e.message ?: "无法连接到API"}"
            )
        } catch (e: Exception) {
            LogManager.e(TAG, "API验证错误", e)
            return@withContext ValidationResult(
                isValid = false,
                apiType = ApiType.INVALID,
                message = "验证错误: ${e.message ?: "未知错误"}"
            )
        }
    }

    /**
     * 检测API数据类型
     * 支持JSON格式和纯文本格式
     */
    private fun detectApiType(data: String, url: String? = null): ApiType {
        // 检查是否是Celestrak TLE格式 (纯文本)
        if (isCelestrakTleFormat(data)) {
            return ApiType.TLE
        }

        // 尝试解析为JSON
        return try {
            // 尝试解析为JSON数组
            val jsonArray = JSONArray(data)
            if (jsonArray.length() == 0) {
                return ApiType.UNKNOWN
            }

            // 获取第一个元素
            val firstItem = jsonArray.getJSONObject(0)
            analyzeJsonStructure(firstItem)
        } catch (e: Exception) {
            // 尝试解析为单个JSON对象
            try {
                val jsonObject = JSONObject(data)
                analyzeJsonStructure(jsonObject)
            } catch (e: Exception) {
                ApiType.INVALID
            }
        }
    }

    /**
     * 检查是否是Celestrak TLE格式
     */
    private fun isCelestrakTleFormat(data: String): Boolean {
        val lines = data.trim().lines()
        if (lines.size < 3) return false

        // 查找至少一组有效的TLE数据
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            // TLE Line 1 以 "1 " 开头，长度为69字符
            // TLE Line 2 以 "2 " 开头，长度为69字符
            if (line.startsWith("1 ") && line.length >= 69) {
                // 检查下一行是否是TLE Line 2
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    if (nextLine.startsWith("2 ") && nextLine.length >= 69) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * 分析JSON结构来确定API类型
     */
    private fun analyzeJsonStructure(json: JSONObject): ApiType {
        val keys = json.keys().asSequence().toList()

        // 检查卫星数据特征
        if (keys.contains("norad_cat_id") && keys.contains("name")) {
            return ApiType.SATELLITE
        }
        if (keys.contains("satellite_id") && keys.contains("name")) {
            return ApiType.SATELLITE
        }

        // 检查转发器数据特征
        if (keys.contains("uuid") && keys.contains("norad_cat_id") &&
            (keys.contains("downlink_low") || keys.contains("uplink_low"))) {
            return ApiType.TRANSMITTER
        }
        if (keys.contains("description") && keys.contains("satellite_id") &&
            (keys.contains("downlink_low") || keys.contains("uplink_low"))) {
            return ApiType.TRANSMITTER
        }

        // 检查TLE数据特征
        if (keys.contains("tle_line1") && keys.contains("tle_line2")) {
            return ApiType.TLE
        }
        if (keys.contains("line1") && keys.contains("line2")) {
            return ApiType.TLE
        }
        if (keys.contains("tle") && keys.contains("satellite_id")) {
            return ApiType.TLE
        }

        // 检查其他可能的字段组合
        if (keys.contains("satelliteId") || keys.contains("satellite_id")) {
            // 可能是卫星相关数据，进一步判断
            if (keys.contains("frequency") || keys.contains("downlink") || keys.contains("uplink")) {
                return ApiType.TRANSMITTER
            }
            if (keys.contains("tleLine1") || keys.contains("tleLine2")) {
                return ApiType.TLE
            }
            return ApiType.SATELLITE
        }

        return ApiType.UNKNOWN
    }

    /**
     * 获取API类型的显示名称
     */
    fun getTypeName(type: ApiType): String {
        return when (type) {
            ApiType.SATELLITE -> "卫星数据API"
            ApiType.TRANSMITTER -> "转发器数据API"
            ApiType.TLE -> "TLE数据API"
            ApiType.UNKNOWN -> "未知类型"
            ApiType.INVALID -> "无效API"
        }
    }

    /**
     * 获取API类型的建议字段
     */
    fun getSuggestedFields(type: ApiType): String {
        return when (type) {
            ApiType.SATELLITE -> "建议字段: norad_cat_id, name, international_designator"
            ApiType.TRANSMITTER -> "建议字段: uuid, norad_cat_id, description, downlink_low, uplink_low"
            ApiType.TLE -> "建议字段: norad_cat_id, tle_line1, tle_line2, epoch"
            else -> ""
        }
    }
}
