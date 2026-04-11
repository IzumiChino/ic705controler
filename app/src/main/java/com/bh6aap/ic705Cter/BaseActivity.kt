package com.bh6aap.ic705Cter

import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import com.bh6aap.ic705Cter.data.LanguageManager
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * 基础Activity类
 * 统一处理语言配置
 */
abstract class BaseActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // 应用语言设置到Context
        val context = try {
            runBlocking {
                LanguageManager.getInstance(newBase).applyLanguageToContext(newBase)
            }
        } catch (e: Exception) {
            newBase
        }
        super.attachBaseContext(context)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 保持应用语言设置 - 使用createConfigurationContext创建新的本地化Context
        runBlocking {
            val locale = LanguageManager.getInstance(this@BaseActivity).getCurrentLocale()
            val config = Configuration(newConfig)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        }
    }
}
