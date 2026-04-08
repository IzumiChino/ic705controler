package com.bh6aap.ic705Cter.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bh6aap.ic705Cter.util.LogManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 过境通知数据存储
 * 用于存储用户选择的过境提醒设置
 */
class PassNotificationDataStore(private val context: Context) {

    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pass_notifications")

        // 默认提醒提前时间（分钟）
        const val DEFAULT_REMINDER_MINUTES = 5
        // 可选的提醒时间选项
        val REMINDER_OPTIONS = listOf(3, 5, 10)

        // 默认仰角阈值
        const val DEFAULT_MIN_ELEVATION = 0
    }

    /**
     * 获取全局提醒提前时间（分钟）
     */
    fun getReminderMinutes(): Flow<Int> {
        val key = intPreferencesKey("reminder_minutes")
        return context.dataStore.data.map { preferences ->
            preferences[key] ?: DEFAULT_REMINDER_MINUTES
        }
    }

    /**
     * 设置全局提醒提前时间
     */
    suspend fun setReminderMinutes(minutes: Int) {
        try {
            val key = intPreferencesKey("reminder_minutes")
            context.dataStore.edit { preferences ->
                preferences[key] = minutes
            }
            LogManager.i("PassNotificationDataStore", "设置提醒时间: ${minutes}分钟")
        } catch (e: Exception) {
            LogManager.e("PassNotificationDataStore", "设置提醒时间失败", e)
        }
    }

    /**
     * 获取仰角阈值（度）
     */
    fun getMinElevation(): Flow<Int> {
        val key = intPreferencesKey("min_elevation")
        return context.dataStore.data.map { preferences ->
            preferences[key] ?: DEFAULT_MIN_ELEVATION
        }
    }

    /**
     * 设置仰角阈值
     */
    suspend fun setMinElevation(elevation: Int) {
        try {
            val key = intPreferencesKey("min_elevation")
            context.dataStore.edit { preferences ->
                preferences[key] = elevation
            }
            LogManager.i("PassNotificationDataStore", "设置仰角阈值: ${elevation}°")
        } catch (e: Exception) {
            LogManager.e("PassNotificationDataStore", "设置仰角阈值失败", e)
        }
    }

    /**
     * 检查是否已启用过境提醒
     * @param noradId 卫星NORAD ID
     * @param aosTime 入境时间（毫秒）
     */
    fun isNotificationEnabled(noradId: String, aosTime: Long): Flow<Boolean> {
        val key = booleanPreferencesKey("pass_${noradId}_${aosTime}")
        return context.dataStore.data.map { preferences ->
            preferences[key] ?: false
        }
    }

    /**
     * 启用过境提醒
     */
    suspend fun enableNotification(noradId: String, aosTime: Long) {
        try {
            val key = booleanPreferencesKey("pass_${noradId}_${aosTime}")
            context.dataStore.edit { preferences ->
                preferences[key] = true
            }
            LogManager.i("PassNotificationDataStore", "启用过境提醒: noradId=$noradId, aosTime=$aosTime")
        } catch (e: Exception) {
            LogManager.e("PassNotificationDataStore", "启用过境提醒失败", e)
        }
    }

    /**
     * 禁用过境提醒
     */
    suspend fun disableNotification(noradId: String, aosTime: Long) {
        try {
            val key = booleanPreferencesKey("pass_${noradId}_${aosTime}")
            context.dataStore.edit { preferences ->
                preferences.remove(key)
            }
            LogManager.i("PassNotificationDataStore", "禁用过境提醒: noradId=$noradId, aosTime=$aosTime")
        } catch (e: Exception) {
            LogManager.e("PassNotificationDataStore", "禁用过境提醒失败", e)
        }
    }

    /**
     * 切换过境提醒状态
     * @return 切换后的状态
     */
    suspend fun toggleNotification(noradId: String, aosTime: Long): Boolean {
        val key = booleanPreferencesKey("pass_${noradId}_${aosTime}")
        var newState = false
        context.dataStore.edit { preferences ->
            val currentState = preferences[key] ?: false
            newState = !currentState
            if (newState) {
                preferences[key] = true
            } else {
                preferences.remove(key)
            }
        }
        LogManager.i("PassNotificationDataStore", "切换过境提醒: noradId=$noradId, aosTime=$aosTime, newState=$newState")
        return newState
    }

    /**
     * 获取所有启用的过境提醒
     */
    fun getAllEnabledNotifications(): Flow<Map<String, Boolean>> {
        return context.dataStore.data.map { preferences ->
            preferences.asMap()
                .filter { it.key.name.startsWith("pass_") }
                .mapKeys { it.key.name }
                .mapValues { it.value as Boolean }
        }
    }

    /**
     * 清理过期的过境提醒（已过期的过境）
     */
    suspend fun cleanupExpiredNotifications(currentTime: Long) {
        try {
            context.dataStore.edit { preferences ->
                val expiredKeys = preferences.asMap()
                    .filter { it.key.name.startsWith("pass_") }
                    .filter {
                        // 解析key获取aosTime，格式: pass_noradId_aosTime
                        val parts = it.key.name.split("_")
                        if (parts.size >= 3) {
                            val aosTime = parts.lastOrNull()?.toLongOrNull() ?: 0
                            aosTime < currentTime
                        } else {
                            false
                        }
                    }
                    .map { it.key }

                expiredKeys.forEach { key ->
                    preferences.remove(key)
                    LogManager.i("PassNotificationDataStore", "清理过期提醒: ${key.name}")
                }
            }
        } catch (e: Exception) {
            LogManager.e("PassNotificationDataStore", "清理过期提醒失败", e)
        }
    }
}
