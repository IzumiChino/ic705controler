package com.bh6aap.ic705Cter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.bh6aap.ic705Cter.R
import com.bh6aap.ic705Cter.util.LogManager

/**
 * 蓝牙前台服务
 * 保持应用在后台运行，防止被系统杀死
 */
class BluetoothForegroundService : Service() {
    
    companion object {
        private const val TAG = "BluetoothForegroundService"
        private const val CHANNEL_ID = "bluetooth_foreground_channel"
        private const val NOTIFICATION_ID = 1
        
        fun startService(context: Context) {
            val intent = Intent(context, BluetoothForegroundService::class.java)
            context.startForegroundService(intent)
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, BluetoothForegroundService::class.java)
            context.stopService(intent)
        }
    }
    
    private val binder = LocalBinder()
    private var notificationManager: NotificationManager? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothForegroundService = this@BluetoothForegroundService
    }
    
    override fun onCreate() {
        super.onCreate()
        LogManager.i(TAG, "【前台服务】服务创建")
        
        // 创建通知渠道
        createNotificationChannel()
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogManager.i(TAG, "【前台服务】服务启动")
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        LogManager.i(TAG, "【前台服务】服务销毁")
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val channel = NotificationChannel(
            CHANNEL_ID,
            "蓝牙连接服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持蓝牙连接活跃，防止应用被系统杀死"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        
        notificationManager?.createNotificationChannel(channel)
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IC-705 蓝牙连接服务")
            .setContentText("保持蓝牙连接活跃")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    /**
     * 更新通知内容
     */
    fun updateNotification(content: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IC-705 蓝牙连接服务")
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
        
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }
}
