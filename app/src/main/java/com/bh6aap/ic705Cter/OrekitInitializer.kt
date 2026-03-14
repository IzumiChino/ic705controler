package com.bh6aap.ic705Cter

import android.content.Context
import org.orekit.data.DataContext
import org.orekit.data.DirectoryCrawler
import java.io.File
import java.io.FileOutputStream

object OrekitInitializer {

    private var isInitialized = false

    /**
     * 初始化 Orekit 数据提供者
     * 从 assets 复制数据到内部存储，然后配置 Orekit
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        val orekitDataDir = File(context.filesDir, "orekit-data")

        // 如果数据目录不存在，从 assets 复制
        if (!orekitDataDir.exists()) {
            copyAssetsToInternalStorage(context, "orekit-data", orekitDataDir)
        }

        // 配置 Orekit 数据提供者 (Orekit 13.x 使用 DataContext)
        val dataContext = DataContext.getDefault()
        dataContext.dataProvidersManager.addProvider(
            DirectoryCrawler(orekitDataDir)
        )

        isInitialized = true
    }

    /**
     * 递归复制 assets 目录到内部存储
     */
    private fun copyAssetsToInternalStorage(context: Context, assetPath: String, destDir: File) {
        val assetManager = context.assets
        val files = assetManager.list(assetPath) ?: return

        if (files.isEmpty()) {
            // 是文件，复制
            destDir.parentFile?.mkdirs()
            assetManager.open(assetPath).use { input ->
                FileOutputStream(destDir).use { output ->
                    input.copyTo(output)
                }
            }
        } else {
            // 是目录，递归复制
            destDir.mkdirs()
            for (file in files) {
                copyAssetsToInternalStorage(
                    context,
                    "$assetPath/$file",
                    File(destDir, file)
                )
            }
        }
    }
}
