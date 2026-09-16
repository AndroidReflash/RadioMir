package com.example.radiomir.repository.`interface`

import com.example.radiomir.dataclasses.TrackInfoDTO
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

interface NetworkRepository {
    val currentTrack: StateFlow<TrackInfoDTO>
    fun getCustomSslOkHttpClient() : OkHttpClient
}