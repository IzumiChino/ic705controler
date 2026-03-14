package com.bh6aap.ic705Cter.data.api

import com.bh6aap.ic705Cter.data.database.entity.CustomTransmitterEntity

/**
 * 将用户自定义数据应用到转发器
 * @param customEntity 用户自定义转发器实体
 * @return 应用自定义数据后的新转发器
 */
fun Transmitter.applyCustomData(customEntity: CustomTransmitterEntity?): Transmitter {
    if (customEntity == null || !customEntity.isEnabled) {
        return this
    }
    
    return this.copy(
        description = customEntity.description ?: this.description,
        uplinkLow = customEntity.uplinkLow ?: this.uplinkLow,
        uplinkHigh = customEntity.uplinkHigh ?: this.uplinkHigh,
        downlinkLow = customEntity.downlinkLow ?: this.downlinkLow,
        downlinkHigh = customEntity.downlinkHigh ?: this.downlinkHigh,
        mode = customEntity.downlinkMode ?: this.mode,
        uplinkMode = customEntity.uplinkMode ?: this.uplinkMode
    )
}

/**
 * 检查转发器是否有用户自定义数据
 * @param customEntity 用户自定义转发器实体
 * @return true=有自定义数据
 */
fun Transmitter.hasCustomData(customEntity: CustomTransmitterEntity?): Boolean {
    if (customEntity == null || !customEntity.isEnabled) {
        return false
    }

    return customEntity.description != null ||
            customEntity.uplinkLow != null ||
            customEntity.uplinkHigh != null ||
            customEntity.downlinkLow != null ||
            customEntity.downlinkHigh != null ||
            customEntity.downlinkMode != null ||
            customEntity.uplinkMode != null
}

/**
 * 将转发器转换为用户自定义实体
 * @return 用户自定义转发器实体
 */
fun Transmitter.toCustomEntity(): CustomTransmitterEntity {
    return CustomTransmitterEntity(
        uuid = this.uuid,
        noradCatId = this.noradCatId,
        description = this.description,
        uplinkLow = this.uplinkLow,
        uplinkHigh = this.uplinkHigh,
        downlinkLow = this.downlinkLow,
        downlinkHigh = this.downlinkHigh,
        downlinkMode = this.mode,
        uplinkMode = this.uplinkMode
    )
}
