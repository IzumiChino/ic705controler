package com.bh6aap.ic705Cter.data.database.entity

/**
 * CW预设实体类
 * 用于存储CW消息预设
 */
data class CwPresetEntity(
    // 主键ID
    val id: Long = 0,

    // 预设索引（0-5，对应六个按钮）
    val presetIndex: Int,

    // 预设名称（显示在按钮上）
    val name: String,

    // CW消息内容
    val message: String,

    // 创建时间
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        // 默认预设名称
        val DEFAULT_NAMES = listOf("CQ", "5NN", "R 599", "73", "QRZ", "AGN")

        // 默认预设消息
        val DEFAULT_MESSAGES = listOf(
            "CQ DE <cl> <cl> PSE K",
            "DE <cl> 5NN BK",
            "DE <cl> UR 599 5NN TU 73 EE",
            "TU 73 EE",
            "QRZ?",
            "PSE AGN"
        )

        /**
         * 创建默认预设列表
         */
        fun createDefaults(): List<CwPresetEntity> {
            return (0..5).map { index ->
                CwPresetEntity(
                    presetIndex = index,
                    name = DEFAULT_NAMES.getOrElse(index) { "预设${index + 1}" },
                    message = DEFAULT_MESSAGES.getOrElse(index) { "" }
                )
            }
        }
    }
}
