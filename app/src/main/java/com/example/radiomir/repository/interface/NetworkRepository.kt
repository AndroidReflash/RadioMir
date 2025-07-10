package com.example.radiomir.repository.`interface`

import com.example.radiomir.dataclasses.TrackInfoDTO
import kotlinx.coroutines.flow.StateFlow

interface NetworkRepository {
    val currentTrack: StateFlow<TrackInfoDTO>
}