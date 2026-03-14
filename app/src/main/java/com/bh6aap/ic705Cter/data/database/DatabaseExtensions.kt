package com.bh6aap.ic705Cter.data.database

import android.content.ContentValues
import android.database.Cursor
import com.bh6aap.ic705Cter.data.database.entity.PassPredictionEntity
import com.bh6aap.ic705Cter.data.database.entity.SatelliteEntity
import com.bh6aap.ic705Cter.data.database.entity.StationEntity
import com.bh6aap.ic705Cter.data.database.entity.SyncRecordEntity

/**
 * Cursor扩展函数 - 转换为实体类
 */

fun Cursor.toSatelliteEntity(): SatelliteEntity {
    return SatelliteEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        noradId = getString(getColumnIndexOrThrow("noradId")),
        internationalDesignator = getString(getColumnIndexOrThrow("internationalDesignator")),
        tleLine1 = getString(getColumnIndexOrThrow("tleLine1")),
        tleLine2 = getString(getColumnIndexOrThrow("tleLine2")),
        category = getString(getColumnIndexOrThrow("category")),
        isFavorite = getInt(getColumnIndexOrThrow("isFavorite")) == 1,
        notes = getString(getColumnIndexOrThrow("notes")),
        createdAt = getLong(getColumnIndexOrThrow("createdAt")),
        updatedAt = getLong(getColumnIndexOrThrow("updatedAt"))
    )
}

fun Cursor.toStationEntity(): StationEntity {
    return StationEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        latitude = getDouble(getColumnIndexOrThrow("latitude")),
        longitude = getDouble(getColumnIndexOrThrow("longitude")),
        altitude = getDouble(getColumnIndexOrThrow("altitude")),
        isDefault = getInt(getColumnIndexOrThrow("isDefault")) == 1,
        minElevation = getDouble(getColumnIndexOrThrow("minElevation")),
        notes = getString(getColumnIndexOrThrow("notes")),
        createdAt = getLong(getColumnIndexOrThrow("createdAt")),
        updatedAt = getLong(getColumnIndexOrThrow("updatedAt"))
    )
}

fun Cursor.toSyncRecordEntity(): SyncRecordEntity {
    return SyncRecordEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        syncType = getString(getColumnIndexOrThrow("syncType")),
        syncTime = getLong(getColumnIndexOrThrow("syncTime")),
        recordCount = getInt(getColumnIndexOrThrow("recordCount")),
        source = getString(getColumnIndexOrThrow("source")),
        isSuccess = getInt(getColumnIndexOrThrow("isSuccess")) == 1,
        errorMessage = getString(getColumnIndexOrThrow("errorMessage"))
    )
}

fun Cursor.toPassPredictionEntity(): PassPredictionEntity {
    return PassPredictionEntity(
        id = getLong(getColumnIndexOrThrow("id")),
        satelliteId = getLong(getColumnIndexOrThrow("satelliteId")),
        stationId = getLong(getColumnIndexOrThrow("stationId")),
        aosTime = getLong(getColumnIndexOrThrow("aosTime")),
        aosAzimuth = getDouble(getColumnIndexOrThrow("aosAzimuth")),
        aosElevation = getDouble(getColumnIndexOrThrow("aosElevation")),
        losTime = getLong(getColumnIndexOrThrow("losTime")),
        losAzimuth = getDouble(getColumnIndexOrThrow("losAzimuth")),
        losElevation = getDouble(getColumnIndexOrThrow("losElevation")),
        maxElevationTime = getLong(getColumnIndexOrThrow("maxElevationTime")),
        maxElevation = getDouble(getColumnIndexOrThrow("maxElevation")),
        duration = getLong(getColumnIndexOrThrow("duration")),
        isNotified = getInt(getColumnIndexOrThrow("isNotified")) == 1,
        isTracked = getInt(getColumnIndexOrThrow("isTracked")) == 1,
        createdAt = getLong(getColumnIndexOrThrow("createdAt"))
    )
}

/**
 * 实体类扩展函数 - 转换为ContentValues
 */

fun SatelliteEntity.toContentValues(): ContentValues {
    return ContentValues().apply {
        if (id != 0L) put("id", id)
        put("name", name)
        put("noradId", noradId)
        put("internationalDesignator", internationalDesignator)
        put("tleLine1", tleLine1)
        put("tleLine2", tleLine2)
        put("category", category)
        put("isFavorite", if (isFavorite) 1 else 0)
        put("notes", notes)
        put("createdAt", createdAt)
        put("updatedAt", System.currentTimeMillis())
    }
}

fun StationEntity.toContentValues(): ContentValues {
    return ContentValues().apply {
        if (id != 0L) put("id", id)
        put("name", name)
        put("latitude", latitude)
        put("longitude", longitude)
        put("altitude", altitude)
        put("isDefault", if (isDefault) 1 else 0)
        put("minElevation", minElevation)
        put("notes", notes)
        put("createdAt", createdAt)
        put("updatedAt", System.currentTimeMillis())
    }
}

fun SyncRecordEntity.toContentValues(): ContentValues {
    return ContentValues().apply {
        if (id != 0L) put("id", id)
        put("syncType", syncType)
        put("syncTime", syncTime)
        put("recordCount", recordCount)
        put("source", source)
        put("isSuccess", if (isSuccess) 1 else 0)
        put("errorMessage", errorMessage)
    }
}

fun PassPredictionEntity.toContentValues(): ContentValues {
    return ContentValues().apply {
        if (id != 0L) put("id", id)
        put("satelliteId", satelliteId)
        put("stationId", stationId)
        put("aosTime", aosTime)
        put("aosAzimuth", aosAzimuth)
        put("aosElevation", aosElevation)
        put("losTime", losTime)
        put("losAzimuth", losAzimuth)
        put("losElevation", losElevation)
        put("maxElevationTime", maxElevationTime)
        put("maxElevation", maxElevation)
        put("duration", duration)
        put("isNotified", if (isNotified) 1 else 0)
        put("isTracked", if (isTracked) 1 else 0)
        put("createdAt", createdAt)
    }
}
