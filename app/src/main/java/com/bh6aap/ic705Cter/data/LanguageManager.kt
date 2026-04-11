package com.bh6aap.ic705Cter.data

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bh6aap.ic705Cter.util.LogManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

/**
 * 语言管理器
 * 负责应用语言切换和系统语言检测
 */
class LanguageManager private constructor(private val context: Context) {

    private val dataStore: DataStore<Preferences> = context.languageDataStore

    /**
     * 支持的语言
     */
    enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
        SYSTEM("system", "跟随系统", "System Default"),
        CHINESE("zh", "简体中文", "简体中文"),
        ENGLISH("en", "English", "English");

        companion object {
            fun fromCode(code: String): AppLanguage {
                return values().find { it.code == code } ?: SYSTEM
            }
        }
    }

    /**
     * 获取当前设置的语言
     */
    val currentLanguage: Flow<AppLanguage> = dataStore.data.map { preferences ->
        val langCode = preferences[LANGUAGE_KEY] ?: AppLanguage.SYSTEM.code
        AppLanguage.fromCode(langCode)
    }

    /**
     * 设置应用语言
     */
    suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language.code
        }
        LogManager.i(TAG, "语言已切换为: ${language.displayName}")
    }

    /**
     * 获取实际使用的Locale
     */
    suspend fun getCurrentLocale(): Locale {
        val lang = currentLanguage.first()
        return when (lang) {
            AppLanguage.SYSTEM -> getSystemLocale()
            AppLanguage.CHINESE -> Locale.CHINESE
            AppLanguage.ENGLISH -> Locale.ENGLISH
        }
    }

    /**
     * 获取系统语言
     */
    fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Resources.getSystem().configuration.locales.get(0)
        } else {
            @Suppress("DEPRECATION")
            Resources.getSystem().configuration.locale
        }
    }

    /**
     * 判断系统语言是否为中文
     */
    fun isSystemLanguageChinese(): Boolean {
        val locale = getSystemLocale()
        return locale.language == Locale.CHINESE.language || locale.language == "zh"
    }

    /**
     * 创建配置好的Context
     */
    fun createLocalizedContext(baseContext: Context, locale: Locale): Context {
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        return baseContext.createConfigurationContext(config)
    }

    /**
     * 应用语言设置到Context
     */
    suspend fun applyLanguageToContext(baseContext: Context): Context {
        val locale = getCurrentLocale()
        return createLocalizedContext(baseContext, locale)
    }

    companion object {
        private const val TAG = "LanguageManager"
        private val LANGUAGE_KEY = stringPreferencesKey("app_language")

        @Volatile
        private var instance: LanguageManager? = null

        private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(name = "language_settings")

        fun getInstance(context: Context): LanguageManager {
            return instance ?: synchronized(this) {
                instance ?: LanguageManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * 在Application中初始化语言设置
         */
        suspend fun initApplicationLanguage(context: Context): Context {
            val manager = getInstance(context)
            return manager.applyLanguageToContext(context)
        }
    }
}
