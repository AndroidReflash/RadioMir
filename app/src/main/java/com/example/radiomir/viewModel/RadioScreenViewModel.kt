package com.example.radiomir.viewModel

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.example.radiomir.RadioService
import com.example.radiomir.dataclasses.TrackInfoDTO
import com.example.radiomir.player.AudioVisualizerConverter
import com.example.radiomir.repository.`interface`.NetworkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
class RadioScreenViewModel(
    appContext: Context,
    scope: CoroutineScope,
    networkRepository: NetworkRepository
): ViewModel() {
    @SuppressLint("StaticFieldLeak")
    private val context = appContext.applicationContext
    @SuppressLint("StaticFieldLeak")
    private var playerService: RadioService? = null
    private val _serviceBound = MutableStateFlow(false)
    private val serviceBound: StateFlow<Boolean> = _serviceBound
    @OptIn(ExperimentalCoroutinesApi::class)
    val isPlaying : StateFlow<Boolean> = serviceBound.flatMapLatest { bound ->
        if (bound && playerService != null) playerService!!.isPlaying else flowOf(false)
    }.stateIn(scope, SharingStarted.Companion.Eagerly, false)

    private val _equalizerArray = MutableStateFlow<List<Double>>(emptyList())
    val equalizerArray : StateFlow<List<Double>> = _equalizerArray

    private val _brightness = MutableStateFlow(0.0f)
    val brightness : StateFlow<Float> = _brightness

    val imageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { networkRepository.getCustomSslOkHttpClient() }))
        }
        .build()
    val currentTrack = serviceBound.flatMapLatest { bound ->
        if (bound && playerService != null) networkRepository.currentTrack else flowOf(TrackInfoDTO())
    }.stateIn(scope, SharingStarted.Companion.Eagerly, TrackInfoDTO())

    init {
        scope.launch(Dispatchers.Main) {
            startSongService()

            launch {
                serviceBound.flatMapLatest { bound ->
                    if (bound && playerService != null) {
                        playerService!!.audioProcessor.onFftData
                    } else {
                        flowOf(emptyList<Double>())
                    }
                }.collect { fftData ->
                    _equalizerArray.value = AudioVisualizerConverter.computeBands(fftData)
                }
            }
            launch {
                pulsing()
            }
        }
    }

    fun isPlaying(){
        playerService?.togglePlay()
    }

    private fun startSongService(){
        if(!serviceBound.value){
            val intent = Intent(context, RadioService::class.java)
            if(playerService == null){
                context.startForegroundService(intent)
            }
            context.bindService(
                intent, serviceConnection, Context.BIND_AUTO_CREATE
            )
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as RadioService.LocalBinder
            playerService = localBinder.getService()
            _serviceBound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerService = null
            _serviceBound.value = false
        }
    }
    private suspend fun CoroutineScope.pulsing(){
        val minBrightness = 0.0f // минимальная яркость
        val maxBrightness = 1.5f // максимальная яркость
        val frameDelay = 16L // ~60 кадров в секунду
        var time = -3.14f / 2 // стартовое отклонение
        val speed = 0.01f // скорость пульсации
        while (isActive){
            val rawSin = sin(time)
            // переводим диапазон [-1.0, 1.0] в [0.0, 1.0]
            val normalizedPulse = (rawSin + 1f) / 2f
            // масштабируем под рамки
            _brightness.value = minBrightness + normalizedPulse * (maxBrightness - minBrightness)
            // шаг вперед по времени и задержка до следующего "кадра"
            time = (time + speed) % (2f * kotlin.math.PI.toFloat())
            delay(frameDelay)
        }
    }
}