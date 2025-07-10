package com.example.radiomir.dataclasses

import android.annotation.SuppressLint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class TrackInfoDTO(

    @SerialName("artist_name")
    val artist: String = "Радио Мир",

    @SerialName("track_name")
    val track: String = "Прямой эфир",

    val album: String = "",

    @SerialName("is_news")
    val isNews: Boolean = false
)
