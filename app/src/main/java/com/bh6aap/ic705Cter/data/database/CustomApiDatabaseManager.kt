package com.bh6aap.ic705Cter.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.bh6aap.ic705Cter.data.database.entity.SatelliteEntity
import com.bh6aap.ic705Cter.data.database.entity.TleEntity
import com.bh6aap.ic705Cter.data.database.entity.TransmitterEntity
import com.bh6aap.ic705Cter.ui.components.ApiSettingsPreferences
import com.bh6aap.ic705Cter.util.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * 自定义API数据库管理器
 * 为每个自定义API源创建独立的数据库
 * 支持卫星数据、TLE数据、转发器数据的分离存储
 */
class CustomApiDatabaseManager private constructor(context: Context) {

    companion object {
        private const val TAG = "CustomApiDatabase"

        // 数据库文件名
        private const val DB_SATELLITE = "custom_satellite.db"
        private const val DB_TLE = "custom_tle.db"
        private const val DB_TRANSMITTER = "custom_transmitter.db"

        // 表名
        private const val TABLE_SATELLITES = "satellites"
        private const val TABLE_TLE = "tle_data"
        private const val TABLE_TRANSMITTERS = "transmitters"
        private const val TABLE_API_METADATA = "api_metadata"

        @Volatile
        private var instance: CustomApiDatabaseManager? = null

        fun getInstance(context: Context): CustomApiDatabaseManager {
            return instance ?: synchronized(this) {
                instance ?: CustomApiDatabaseManager(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * 检查是否使用自定义API
         */
        fun isUsingCustomApi(context: Context): Boolean {
            val prefs = ApiSettingsPreferences.getInstance(context)
            return prefs.getSatelliteApiUrl().isNotBlank() ||
                   prefs.getTleApiUrl().isNotBlank() ||
                   prefs.getTransmitterApiUrl().isNotBlank()
        }

        /**
         * 获取当前应该使用的数据库类型
         * @return true=使用自定义数据库, false=使用默认数据库
         */
        fun shouldUseCustomDatabase(context: Context): Boolean {
            return isUsingCustomApi(context)
        }
    }

    private val context = context.applicationContext

    // 三个独立的数据库帮助类
    private var satelliteDbHelper: CustomSatelliteDbHelper? = null
    private var tleDbHelper: CustomTleDbHelper? = null
    private var transmitterDbHelper: CustomTransmitterDbHelper? = null

    /**
     * 初始化自定义数据库
     */
    fun initializeDatabases() {
        synchronized(this) {
            if (satelliteDbHelper == null) {
                satelliteDbHelper = CustomSatelliteDbHelper(context)
                LogManager.i(TAG, "初始化自定义卫星数据库")
            }
            if (tleDbHelper == null) {
                tleDbHelper = CustomTleDbHelper(context)
                LogManager.i(TAG, "初始化自定义TLE数据库")
            }
            if (transmitterDbHelper == null) {
                transmitterDbHelper = CustomTransmitterDbHelper(context)
                LogManager.i(TAG, "初始化自定义转发器数据库")
            }
        }
    }

    /**
     * 关闭所有自定义数据库
     */
    fun closeDatabases() {
        synchronized(this) {
            satelliteDbHelper?.close()
            satelliteDbHelper = null
            tleDbHelper?.close()
            tleDbHelper = null
            transmitterDbHelper?.close()
            transmitterDbHelper = null
            LogManager.i(TAG, "关闭所有自定义数据库")
        }
    }

    /**
     * 清除所有自定义数据
     */
    fun clearAllData() {
        satelliteDbHelper?.clearData()
        tleDbHelper?.clearData()
        transmitterDbHelper?.clearData()
        LogManager.i(TAG, "清除所有自定义数据库数据")
    }

    // ==================== 卫星数据操作 ====================

    suspend fun saveSatellites(satellites: List<SatelliteEntity>) = withContext(Dispatchers.IO) {
        val db = satelliteDbHelper?.writableDatabase ?: return@withContext
        db.beginTransaction()
        try {
            db.delete(TABLE_SATELLITES, null, null)
            satellites.forEach { satellite ->
                val values = ContentValues().apply {
                    put("norad_id", satellite.noradId)
                    put("name", satellite.name)
                    put("international_designator", satellite.internationalDesignator)
                    put("tle_line1", satellite.tleLine1)
                    put("tle_line2", satellite.tleLine2)
                    put("category", satellite.category)
                    put("is_favorite", if (satellite.isFavorite) 1 else 0)
                    put("notes", satellite.notes)
                    put("updated_at", System.currentTimeMillis())
                }
                db.insertWithOnConflict(TABLE_SATELLITES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
            LogManager.i(TAG, "保存 ${satellites.size} 颗卫星到自定义数据库")
        } finally {
            db.endTransaction()
        }
    }

    suspend fun getAllSatellites(): List<SatelliteEntity> = withContext(Dispatchers.IO) {
        val db = satelliteDbHelper?.readableDatabase ?: return@withContext emptyList()
        val satellites = mutableListOf<SatelliteEntity>()
        db.query(TABLE_SATELLITES, null, null, null, null, null, "name ASC").use { cursor ->
            while (cursor.moveToNext()) {
                satellites.add(SatelliteEntity(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    noradId = cursor.getString(cursor.getColumnIndexOrThrow("norad_id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    internationalDesignator = cursor.getString(cursor.getColumnIndexOrThrow("international_designator")),
                    tleLine1 = cursor.getString(cursor.getColumnIndexOrThrow("tle_line1")),
                    tleLine2 = cursor.getString(cursor.getColumnIndexOrThrow("tle_line2")),
                    category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
                    isFavorite = cursor.getInt(cursor.getColumnIndexOrThrow("is_favorite")) == 1,
                    notes = cursor.getString(cursor.getColumnIndexOrThrow("notes"))
                ))
            }
        }
        return@withContext satellites
    }

    fun getAllSatellitesFlow(): Flow<List<SatelliteEntity>> = flow {
        emit(getAllSatellites())
    }

    // ==================== TLE数据操作 ====================

    suspend fun saveTleData(tleList: List<TleEntity>) = withContext(Dispatchers.IO) {
        val db = tleDbHelper?.writableDatabase ?: return@withContext
        db.beginTransaction()
        try {
            db.delete(TABLE_TLE, null, null)
            tleList.forEach { tle ->
                val values = ContentValues().apply {
                    put("norad_id", tle.noradId)
                    put("tle_line1", tle.tleLine1)
                    put("tle_line2", tle.tleLine2)
                    put("epoch", tle.epoch)
                    put("updated_at", System.currentTimeMillis())
                }
                db.insertWithOnConflict(TABLE_TLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
            LogManager.i(TAG, "保存 ${tleList.size} 条TLE数据到自定义数据库")
        } finally {
            db.endTransaction()
        }
    }

    suspend fun getTleByNoradId(noradId: String): TleEntity? = withContext(Dispatchers.IO) {
        val db = tleDbHelper?.readableDatabase ?: return@withContext null
        db.query(TABLE_TLE, null, "norad_id = ?", arrayOf(noradId), null, null, null).use { cursor ->
            if (cursor.moveToFirst()) {
                return@withContext TleEntity(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    noradId = cursor.getString(cursor.getColumnIndexOrThrow("norad_id")),
                    tleLine1 = cursor.getString(cursor.getColumnIndexOrThrow("tle_line1")),
                    tleLine2 = cursor.getString(cursor.getColumnIndexOrThrow("tle_line2")),
                    epoch = cursor.getString(cursor.getColumnIndexOrThrow("epoch"))
                )
            }
        }
        return@withContext null
    }

    // ==================== 转发器数据操作 ====================

    suspend fun saveTransmitters(transmitters: List<TransmitterEntity>) = withContext(Dispatchers.IO) {
        val db = transmitterDbHelper?.writableDatabase ?: return@withContext
        db.beginTransaction()
        try {
            db.delete(TABLE_TRANSMITTERS, null, null)
            transmitters.forEach { transmitter ->
                val values = ContentValues().apply {
                    put("uuid", transmitter.uuid)
                    put("norad_id", transmitter.noradId)
                    put("description", transmitter.description)
                    put("uplink_low", transmitter.uplinkLow)
                    put("uplink_high", transmitter.uplinkHigh)
                    put("downlink_low", transmitter.downlinkLow)
                    put("downlink_high", transmitter.downlinkHigh)
                    put("mode", transmitter.mode)
                    put("uplink_mode", transmitter.uplinkMode)
                    put("invert", if (transmitter.invert) 1 else 0)
                    put("updated_at", System.currentTimeMillis())
                }
                db.insertWithOnConflict(TABLE_TRANSMITTERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
            LogManager.i(TAG, "保存 ${transmitters.size} 个转发器到自定义数据库")
        } finally {
            db.endTransaction()
        }
    }

    suspend fun getTransmittersByNoradId(noradId: Int): List<TransmitterEntity> = withContext(Dispatchers.IO) {
        val db = transmitterDbHelper?.readableDatabase ?: return@withContext emptyList<TransmitterEntity>()
        val transmitters = mutableListOf<TransmitterEntity>()
        db.query(TABLE_TRANSMITTERS, null, "norad_id = ?", arrayOf(noradId.toString()), null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                transmitters.add(TransmitterEntity(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    uuid = cursor.getString(cursor.getColumnIndexOrThrow("uuid")),
                    noradId = cursor.getInt(cursor.getColumnIndexOrThrow("norad_id")),
                    description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                    uplinkLow = cursor.getLong(cursor.getColumnIndexOrThrow("uplink_low")),
                    uplinkHigh = cursor.getLong(cursor.getColumnIndexOrThrow("uplink_high")),
                    downlinkLow = cursor.getLong(cursor.getColumnIndexOrThrow("downlink_low")),
                    downlinkHigh = cursor.getLong(cursor.getColumnIndexOrThrow("downlink_high")),
                    mode = cursor.getString(cursor.getColumnIndexOrThrow("mode")),
                    uplinkMode = cursor.getString(cursor.getColumnIndexOrThrow("uplink_mode")),
                    invert = cursor.getInt(cursor.getColumnIndexOrThrow("invert")) == 1
                ))
            }
        }
        return@withContext transmitters
    }

    // ==================== 内部数据库帮助类 ====================

    private class CustomSatelliteDbHelper(context: Context) : SQLiteOpenHelper(
        context, DB_SATELLITE, null, 1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_SATELLITES (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    norad_id TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    international_designator TEXT,
                    tle_line1 TEXT,
                    tle_line2 TEXT,
                    category TEXT,
                    is_favorite INTEGER DEFAULT 0,
                    notes TEXT,
                    updated_at INTEGER DEFAULT ${System.currentTimeMillis()}
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_API_METADATA (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    api_url TEXT NOT NULL,
                    api_type TEXT NOT NULL,
                    last_sync INTEGER DEFAULT 0,
                    version TEXT
                )
            """.trimIndent())
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_SATELLITES")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_API_METADATA")
            onCreate(db)
        }

        fun clearData() {
            writableDatabase.delete(TABLE_SATELLITES, null, null)
        }
    }

    private class CustomTleDbHelper(context: Context) : SQLiteOpenHelper(
        context, DB_TLE, null, 1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_TLE (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    norad_id TEXT NOT NULL UNIQUE,
                    tle_line1 TEXT NOT NULL,
                    tle_line2 TEXT NOT NULL,
                    epoch TEXT,
                    updated_at INTEGER DEFAULT ${System.currentTimeMillis()}
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_API_METADATA (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    api_url TEXT NOT NULL,
                    api_type TEXT NOT NULL,
                    last_sync INTEGER DEFAULT 0,
                    version TEXT
                )
            """.trimIndent())
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_TLE")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_API_METADATA")
            onCreate(db)
        }

        fun clearData() {
            writableDatabase.delete(TABLE_TLE, null, null)
        }
    }

    private class CustomTransmitterDbHelper(context: Context) : SQLiteOpenHelper(
        context, DB_TRANSMITTER, null, 1
    ) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_TRANSMITTERS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uuid TEXT NOT NULL UNIQUE,
                    norad_id INTEGER NOT NULL,
                    description TEXT,
                    uplink_low INTEGER,
                    uplink_high INTEGER,
                    downlink_low INTEGER,
                    downlink_high INTEGER,
                    mode TEXT,
                    uplink_mode TEXT,
                    invert INTEGER DEFAULT 0,
                    updated_at INTEGER DEFAULT ${System.currentTimeMillis()}
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS $TABLE_API_METADATA (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    api_url TEXT NOT NULL,
                    api_type TEXT NOT NULL,
                    last_sync INTEGER DEFAULT 0,
                    version TEXT
                )
            """.trimIndent())
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_TRANSMITTERS")
            db.execSQL("DROP TABLE IF EXISTS $TABLE_API_METADATA")
            onCreate(db)
        }

        fun clearData() {
            writableDatabase.delete(TABLE_TRANSMITTERS, null, null)
        }
    }
}
