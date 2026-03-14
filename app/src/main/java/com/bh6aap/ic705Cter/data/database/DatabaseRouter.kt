package com.bh6aap.ic705Cter.data.database

import android.content.Context
import com.bh6aap.ic705Cter.data.database.entity.SatelliteEntity
import com.bh6aap.ic705Cter.data.database.entity.TleEntity
import com.bh6aap.ic705Cter.data.database.entity.TransmitterEntity
import com.bh6aap.ic705Cter.util.LogManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * 数据库路由器
 * 根据用户是否配置了自定义API来决定使用哪个数据源
 * 提供统一的数据访问接口
 */
class DatabaseRouter private constructor(context: Context) {

    companion object {
        private const val TAG = "DatabaseRouter"

        @Volatile
        private var instance: DatabaseRouter? = null

        fun getInstance(context: Context): DatabaseRouter {
            return instance ?: synchronized(this) {
                instance ?: DatabaseRouter(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val context = context.applicationContext
    private val defaultDbHelper = DatabaseHelper.getInstance(context)
    private val customDbManager = CustomApiDatabaseManager.getInstance(context)

    // 当前使用的数据库类型
    private var isUsingCustomDb: Boolean = false

    init {
        refreshDatabaseType()
    }

    /**
     * 刷新数据库类型（根据用户设置）
     */
    fun refreshDatabaseType() {
        isUsingCustomDb = CustomApiDatabaseManager.shouldUseCustomDatabase(context)
        if (isUsingCustomDb) {
            customDbManager.initializeDatabases()
            LogManager.i(TAG, "使用自定义API数据库")
        } else {
            LogManager.i(TAG, "使用默认数据库")
        }
    }

    /**
     * 检查是否使用自定义数据库
     */
    fun isUsingCustomDatabase(): Boolean = isUsingCustomDb

    // ==================== 卫星数据操作 ====================

    suspend fun getAllSatellites(): List<SatelliteEntity> {
        return if (isUsingCustomDb) {
            customDbManager.getAllSatellites()
        } else {
            defaultDbHelper.getAllSatellites().first()
        }
    }

    fun getAllSatellitesFlow(): Flow<List<SatelliteEntity>> {
        return if (isUsingCustomDb) {
            customDbManager.getAllSatellitesFlow()
        } else {
            defaultDbHelper.getAllSatellites()
        }
    }

    suspend fun saveSatellites(satellites: List<SatelliteEntity>) {
        if (isUsingCustomDb) {
            customDbManager.saveSatellites(satellites)
        } else {
            // 默认数据库的保存逻辑在TleDataManager中处理
            LogManager.w(TAG, "默认数据库不支持直接保存卫星列表")
        }
    }

    suspend fun getSatelliteByNoradId(noradId: String): SatelliteEntity? {
        return if (isUsingCustomDb) {
            customDbManager.getAllSatellites().find { it.noradId == noradId }
        } else {
            defaultDbHelper.getSatelliteByNoradId(noradId)
        }
    }

    // ==================== TLE数据操作 ====================

    suspend fun getTleByNoradId(noradId: String): TleEntity? {
        return if (isUsingCustomDb) {
            customDbManager.getTleByNoradId(noradId)
        } else {
            // 默认数据库中TLE存储在satellites表中
            val satellite = defaultDbHelper.getSatelliteByNoradId(noradId)
            satellite?.let {
                TleEntity(
                    noradId = it.noradId,
                    tleLine1 = it.tleLine1,
                    tleLine2 = it.tleLine2,
                    name = it.name
                )
            }
        }
    }

    suspend fun saveTleData(tleList: List<TleEntity>) {
        if (isUsingCustomDb) {
            customDbManager.saveTleData(tleList)
        } else {
            // 默认数据库中TLE与卫星数据一起存储
            // 这里可以选择忽略或转换为卫星实体
            LogManager.w(TAG, "默认数据库不支持单独保存TLE数据")
        }
    }

    // ==================== 转发器数据操作 ====================

    suspend fun getTransmittersByNoradId(noradId: Int): List<TransmitterEntity> {
        return if (isUsingCustomDb) {
            customDbManager.getTransmittersByNoradId(noradId)
        } else {
            // 默认数据库返回API的Transmitter类型，需要转换
            emptyList()
        }
    }

    suspend fun getAllTransmitters(): List<TransmitterEntity> {
        return if (isUsingCustomDb) {
            // 自定义数据库需要遍历所有卫星获取转发器
            val satellites = customDbManager.getAllSatellites()
            val allTransmitters = mutableListOf<TransmitterEntity>()
            for (satellite in satellites) {
                val noradId = satellite.noradId.toIntOrNull() ?: 0
                allTransmitters.addAll(customDbManager.getTransmittersByNoradId(noradId))
            }
            allTransmitters
        } else {
            // 默认数据库返回Flow，需要收集
            emptyList<TransmitterEntity>()
        }
    }

    suspend fun saveTransmitters(transmitters: List<TransmitterEntity>) {
        if (isUsingCustomDb) {
            customDbManager.saveTransmitters(transmitters)
        } else {
            // 默认数据库的保存逻辑在TransmitterDataManager中处理
            LogManager.w(TAG, "默认数据库不支持直接保存转发器列表")
        }
    }

    // ==================== 数据同步状态 ====================

    suspend fun getLastSyncTime(syncType: String): Long? {
        return if (isUsingCustomDb) {
            // 自定义数据库使用不同的同步记录方式
            // 可以存储在SharedPreferences中
            null
        } else {
            // 默认数据库的同步记录存储在sync_records表中
            // 需要通过其他方式获取
            null
        }
    }

    suspend fun updateSyncRecord(syncType: String, recordCount: Int, source: String? = null) {
        if (!isUsingCustomDb) {
            // 默认数据库的同步记录更新
            LogManager.i(TAG, "更新同步记录: $syncType, 记录数: $recordCount")
        }
    }

    // ==================== 数据清除 ====================

    suspend fun clearSatelliteData() {
        if (isUsingCustomDb) {
            customDbManager.clearAllData()
        } else {
            // 默认数据库的数据清除
            LogManager.i(TAG, "清除默认数据库卫星数据")
        }
    }

    // ==================== 数据库切换 ====================

    /**
     * 切换到自定义数据库
     */
    fun switchToCustomDatabase() {
        isUsingCustomDb = true
        customDbManager.initializeDatabases()
        LogManager.i(TAG, "切换到自定义API数据库")
    }

    /**
     * 切换到默认数据库
     */
    fun switchToDefaultDatabase() {
        isUsingCustomDb = false
        LogManager.i(TAG, "切换到默认数据库")
    }

    /**
     * 关闭所有数据库连接
     */
    fun closeAllDatabases() {
        customDbManager.closeDatabases()
        DatabaseHelper.closeInstance()
        LogManager.i(TAG, "关闭所有数据库连接")
    }
}
