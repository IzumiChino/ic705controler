package com.bh6aap.ic705Cter.data.api

import com.google.gson.annotations.SerializedName

/**
 * 卫星转发器数据类
 * 对应 SatNOGS API 的 Transmitter 数据结构
 */
data class Transmitter(
    @SerializedName("uuid")
    val uuid: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("alive")
    val alive: Boolean,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("uplink_low")
    val uplinkLow: Long?,
    
    @SerializedName("uplink_high")
    val uplinkHigh: Long?,
    
    @SerializedName("uplink_drift")
    val uplinkDrift: Double?,
    
    @SerializedName("downlink_low")
    val downlinkLow: Long?,
    
    @SerializedName("downlink_high")
    val downlinkHigh: Long?,
    
    @SerializedName("downlink_drift")
    val downlinkDrift: Double?,
    
    @SerializedName("mode")
    val mode: String,
    
    @SerializedName("mode_id")
    val modeId: Int,
    
    @SerializedName("uplink_mode")
    val uplinkMode: String?,
    
    @SerializedName("invert")
    val invert: Boolean,
    
    @SerializedName("baud")
    val baud: Double?,
    
    @SerializedName("sat_id")
    val satId: String,
    
    @SerializedName("norad_cat_id")
    val noradCatId: Int,
    
    @SerializedName("norad_follow_id")
    val noradFollowId: Int?,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("updated")
    val updated: String,
    
    @SerializedName("citation")
    val citation: String,
    
    @SerializedName("service")
    val service: String,
    
    @SerializedName("iaru_coordination")
    val iaruCoordination: String,
    
    @SerializedName("iaru_coordination_url")
    val iaruCoordinationUrl: String,
    
    @SerializedName("frequency_violation")
    val frequencyViolation: Boolean,
    
    @SerializedName("unconfirmed")
    val unconfirmed: Boolean
)
