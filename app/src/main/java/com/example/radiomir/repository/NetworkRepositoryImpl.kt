package com.example.radiomir.repository

import android.annotation.SuppressLint
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.example.radiomir.constants.Url
import com.example.radiomir.dataclasses.TrackInfoDTO
import com.example.radiomir.repository.`interface`.NetworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class NetworkRepositoryImpl(
    private val scope: CoroutineScope,
): NetworkRepository {
    init {
        scope.launch {
            withContext(Dispatchers.IO){
                while (isActive){
                    getDataCurrentTrack()
                    delay(15000)
                }
            }
        }
    }

    private val _currentTrack = MutableStateFlow(TrackInfoDTO())
    override val currentTrack : StateFlow<TrackInfoDTO> = _currentTrack

    private suspend fun setConnection(link: String): HttpURLConnection {
        val url = URL(link)
        val connection = withContext(Dispatchers.IO) {
            url.openConnection()
        } as HttpURLConnection
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        )
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "GET"
        return connection
    }

    private suspend fun getDataCurrentTrack(){
        val connection = setConnection(Url.TrackInfo.value)
        try {
            withContext(Dispatchers.IO) { connection.connect() }
            val response = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            Log.d("RadioListValue", response)
            if (response.isEmpty() || response == "null" || response == "[]") {
                Log.println(Log.DEBUG, "NextTracks", "Пустой ответ")
                return
            }

            _currentTrack.value = try {
                Json.decodeFromString(response)
            } catch (e: Exception) {
                Log.e("RadioListValue", "Ошибка парсинга: ${e.message}")
                TrackInfoDTO()
            }
            Log.d("RadioListValue", "${_currentTrack.value}")
        } catch (e: SocketTimeoutException) {
            Log.println(Log.DEBUG, "RadioListValue", "Сервер не ответил вовремя: ${e.message}")
        } catch (e: Exception) {
            Log.println(Log.DEBUG, "RadioListValue", "Неизвестная ошибка NextTracks: ${e.message}")
        }
    }
}

@UnstableApi
object NetworkManager {
    @SuppressLint("StaticFieldLeak")
    @Volatile private var instance: NetworkRepository? = null

    fun getInstance(
        scope: CoroutineScope
    ): NetworkRepository {
        return instance ?: synchronized(this) {
            instance ?: NetworkRepositoryImpl(scope).also {
                instance = it
            }
        }
    }
}