package com.bh6aap.ic705Cter

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.bh6aap.ic705Cter.data.LanguageManager
import com.bh6aap.ic705Cter.util.LogManager
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * 应用入口类
 * 负责全局初始化和语言配置
 */
class IC705Application : Application() {

    override fun attachBaseContext(base: Context) {
        // 在应用启动时初始化语言设置
        val context = try {
            runBlocking {
                LanguageManager.initApplicationLanguage(base)
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "语言初始化失败", e)
            base
        }
        super.attachBaseContext(context)
    }

    override fun onCreate() {
        super.onCreate()
        LogManager.i(TAG, "应用启动")
    }

    /**
     * 当配置改变时（如语言切换）
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 保持应用语言设置，不被系统配置覆盖
        runBlocking {
            val locale = LanguageManager.getInstance(this@IC705Application).getCurrentLocale()
            val config = Configuration(newConfig)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }

    companion object {
        private const val TAG = "IC705Application"
    }
}
