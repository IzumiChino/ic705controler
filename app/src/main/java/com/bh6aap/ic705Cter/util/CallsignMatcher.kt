package com.bh6aap.ic705Cter.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 呼号模糊匹配器
 * 用于在卫星跟踪界面输入呼号时提供模糊搜索建议
 */
class CallsignMatcher private constructor(context: Context) {

    private val callsigns = mutableListOf<String>()

    init {
        loadCallsigns(context)
    }

    /**
     * 从内部存储、assets或raw资源加载呼号列表
     * 优先从内部存储的callsigns_custom.txt加载，如果不存在则从assets/callsigns.txt加载
     */
    private fun loadCallsigns(context: Context) {
        try {
            // 首先尝试从内部存储加载（用户自定义的呼号库）
            val customFile = context.getFileStreamPath("callsigns_custom.txt")
            if (customFile.exists()) {
                context.openFileInput("callsigns_custom.txt").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.lineSequence().forEach { line ->
                            val trimmed = line.trim().uppercase()
                            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                callsigns.add(trimmed)
                            }
                        }
                    }
                }
                LogManager.i(TAG, "从内部存储加载了 ${callsigns.size} 个呼号")
            } else {
                // 尝试从assets加载
                try {
                    val assetManager = context.assets
                    val inputStream = assetManager.open("callsigns.txt")
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        reader.lineSequence().forEach { line ->
                            val trimmed = line.trim().uppercase()
                            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                callsigns.add(trimmed)
                            }
                        }
                    }
                    LogManager.i(TAG, "从assets加载了 ${callsigns.size} 个呼号")
                } catch (e: Exception) {
                    // 如果assets加载失败，尝试从raw资源加载
                    try {
                        val resourceId = context.resources.getIdentifier("callsigns", "raw", context.packageName)
                        if (resourceId != 0) {
                            context.resources.openRawResource(resourceId).use { inputStream ->
                                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                    reader.lineSequence().forEach { line ->
                                        val trimmed = line.trim().uppercase()
                                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                                            callsigns.add(trimmed)
                                        }
                                    }
                                }
                            }
                            LogManager.i(TAG, "从raw资源加载了 ${callsigns.size} 个呼号")
                        } else {
                            LogManager.w(TAG, "未找到呼号资源文件")
                        }
                    } catch (e2: Exception) {
                        LogManager.e(TAG, "加载呼号列表失败", e2)
                    }
                }
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "加载呼号列表失败", e)
        }
    }

    /**
     * 重新加载呼号列表（用于呼号库更新后刷新）
     */
    fun reloadCallsigns(context: Context) {
        callsigns.clear()
        loadCallsigns(context)
        LogManager.i(TAG, "重新加载呼号列表，当前共 ${callsigns.size} 个呼号")
    }

    /**
     * 模糊搜索呼号
     * @param query 搜索关键词
     * @param limit 返回结果数量限制（默认6个）
     * @return 匹配的呼号列表
     */
    fun search(query: String, limit: Int = 6): List<String> {
        if (query.isBlank() || callsigns.isEmpty()) {
            return emptyList()
        }

        val upperQuery = query.trim().uppercase()
        
        // 计算每个呼号的匹配分数
        val scoredMatches = callsigns.mapNotNull { callsign ->
            val score = calculateMatchScore(callsign, upperQuery)
            if (score > 0) callsign to score else null
        }

        // 按分数降序排序，取前limit个
        return scoredMatches
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * 异步搜索呼号
     */
    suspend fun searchAsync(query: String, limit: Int = 6): List<String> = withContext(Dispatchers.Default) {
        search(query, limit)
    }

    /**
     * 计算匹配分数
     * 分数越高表示匹配度越高
     */
    private fun calculateMatchScore(callsign: String, query: String): Int {
        val upperCallsign = callsign.uppercase()
        var score = 0

        // 完全匹配得最高分
        if (upperCallsign == query) {
            return 1000
        }

        // 开头匹配得分较高
        if (upperCallsign.startsWith(query)) {
            score += 500 + query.length * 10
        }

        // 包含查询字符串
        if (upperCallsign.contains(query)) {
            score += 300 + query.length * 5
            // 查询字符串在呼号中的位置越靠前，分数越高
            val index = upperCallsign.indexOf(query)
            score += (upperCallsign.length - index) * 2
        }

        // 计算编辑距离（Levenshtein距离）
        val distance = levenshteinDistance(upperCallsign, query)
        if (distance <= 2) {
            score += 200 - distance * 50
        }

        // 逐字符匹配（用于处理输入错误的情况）
        var consecutiveMatches = 0
        var maxConsecutive = 0
        var queryIndex = 0
        
        for (char in upperCallsign) {
            if (queryIndex < query.length && char == query[queryIndex]) {
                consecutiveMatches++
                queryIndex++
                maxConsecutive = maxOf(maxConsecutive, consecutiveMatches)
            } else {
                consecutiveMatches = 0
            }
        }
        
        // 连续匹配的字符越多，分数越高
        score += maxConsecutive * 20

        return score
    }

    /**
     * 计算Levenshtein编辑距离
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        
        if (m == 0) return n
        if (n == 0) return m
        
        // 使用滚动数组优化空间复杂度
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,      // 删除
                    curr[j - 1] + 1,  // 插入
                    prev[j - 1] + cost // 替换
                )
            }
            val temp = prev
            prev = curr
            curr = temp
        }
        
        return prev[n]
    }

    /**
     * 获取所有呼号数量
     */
    fun getCallsignCount(): Int = callsigns.size

    companion object {
        private const val TAG = "CallsignMatcher"
        
        @Volatile
        private var instance: CallsignMatcher? = null

        /**
         * 获取单例实例
         */
        fun getInstance(context: Context): CallsignMatcher {
            return instance ?: synchronized(this) {
                instance ?: CallsignMatcher(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * 初始化呼号匹配器
         * 建议在应用启动时调用，提前加载呼号数据
         */
        fun initialize(context: Context) {
            getInstance(context)
        }
    }
}
