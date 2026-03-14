package com.bh6aap.ic705Cter.data.api

import com.google.gson.annotations.SerializedName

/**
 * 卫星详细信息数据类
 * 对应 SatNOGS API /api/satellites/ 的数据结构
 */
data class SatelliteInfo(
    @SerializedName("sat_id")
    val satId: String,

    @SerializedName("norad_cat_id")
    val noradCatId: Int,

    @SerializedName("norad_follow_id")
    val noradFollowId: Int?,

    @SerializedName("name")
    val name: String,

    @SerializedName("names")
    val names: String?,  // 别名，可能包含多个名称

    @SerializedName("image")
    val image: String?,

    @SerializedName("status")
    val status: String,

    @SerializedName("decayed")
    val decayed: String?,

    @SerializedName("launched")
    val launched: String?,

    @SerializedName("deployed")
    val deployed: String?,

    @SerializedName("website")
    val website: String?,

    @SerializedName("operator")
    val operator: String?,

    @SerializedName("countries")
    val countries: String?,

    @SerializedName("telemetries")
    val telemetries: List<String>?,

    @SerializedName("updated")
    val updated: String,

    @SerializedName("citation")
    val citation: String?,

    @SerializedName("is_frequency_violator")
    val isFrequencyViolator: Boolean,

    @SerializedName("associated_satellites")
    val associatedSatellites: List<String>?
) {
    /**
     * 获取所有别名列表
     */
    fun getAliasList(): List<String> {
        if (names.isNullOrBlank()) return emptyList()
        return names.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    /**
     * 获取完整名称（主名称 + 别名）
     */
    fun getFullName(): String {
        val aliasList = getAliasList()
        return if (aliasList.isNotEmpty()) {
            "$name (${aliasList.joinToString(", ")})"
        } else {
            name
        }
    }
}
