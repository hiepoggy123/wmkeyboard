package com.wasimaster.wmkeyboard.core.settings

import kotlinx.serialization.Serializable

@Serializable
data class AiPredictionSettings(
    val enabled: Boolean = true,
    val shorthandPrefixMode: Boolean = false,
)
