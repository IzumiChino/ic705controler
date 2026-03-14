package com.bh6aap.ic705Cter.data.radio

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences

/**
 * 蓝牙设备偏好设置管理器
 * 用于存储和读取默认蓝牙设备，实现一键连接功能
 */
class BluetoothDevicePreference private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "bluetooth_device_prefs"
        private const val KEY_DEFAULT_DEVICE_NAME = "default_device_name"
        private const val KEY_DEFAULT_DEVICE_ADDRESS = "default_device_address"

        @Volatile
        private var instance: BluetoothDevicePreference? = null

        fun getInstance(context: Context): BluetoothDevicePreference {
            return instance ?: synchronized(this) {
                instance ?: BluetoothDevicePreference(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    /**
     * 保存默认蓝牙设备
     * @param device 蓝牙设备
     */
    fun saveDefaultDevice(device: BluetoothDevice) {
        prefs.edit().apply {
            putString(KEY_DEFAULT_DEVICE_NAME, device.name)
            putString(KEY_DEFAULT_DEVICE_ADDRESS, device.address)
            apply()
        }
    }

    /**
     * 获取默认蓝牙设备地址
     * @return 设备地址，如果没有设置则返回null
     */
    fun getDefaultDeviceAddress(): String? {
        return prefs.getString(KEY_DEFAULT_DEVICE_ADDRESS, null)
    }

    /**
     * 获取默认蓝牙设备名称
     * @return 设备名称，如果没有设置则返回null
     */
    fun getDefaultDeviceName(): String? {
        return prefs.getString(KEY_DEFAULT_DEVICE_NAME, null)
    }

    /**
     * 检查是否已设置默认设备
     * @return true 如果已设置默认设备
     */
    fun hasDefaultDevice(): Boolean {
        return !getDefaultDeviceAddress().isNullOrEmpty()
    }

    /**
     * 清除默认设备设置
     */
    fun clearDefaultDevice() {
        prefs.edit().apply {
            remove(KEY_DEFAULT_DEVICE_NAME)
            remove(KEY_DEFAULT_DEVICE_ADDRESS)
            apply()
        }
    }

    /**
     * 从已配对设备列表中查找默认设备
     * @param pairedDevices 已配对设备列表
     * @return 默认设备，如果找不到则返回null
     */
    fun findDefaultDevice(pairedDevices: List<BluetoothDevice>): BluetoothDevice? {
        val defaultAddress = getDefaultDeviceAddress() ?: return null
        return pairedDevices.find { it.address == defaultAddress }
    }
}
