package com.example.radiomir

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.SessionCommand
import androidx.media3.ui.PlayerNotificationManager.ACTION_STOP
import com.example.radiomir.constants.Keywords
import com.example.radiomir.constants.Url
import com.example.radiomir.player.AudioProcessorWithCallback
import com.example.radiomir.player.CustomRenderersFactory
import com.example.radiomir.repository.NetworkManager
import com.example.radiomir.repository.`interface`.NetworkRepository
import com.radiomir.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess
import androidx.media.app.NotificationCompat.MediaStyle

@UnstableApi
class RadioService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var networkRepository : NetworkRepository? = null
    lateinit var audioProcessor: AudioProcessorWithCallback
    private lateinit var exoPlayer: ExoPlayer
    private val _mediaSession = MutableStateFlow<MediaSession?>(null)
    private val mediaSession: StateFlow<MediaSession?> = _mediaSession
    private lateinit var notificationManager: NotificationManager

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying : StateFlow<Boolean> = _isPlaying

    inner class LocalBinder : Binder() {
        fun getService(): RadioService = this@RadioService
    }
    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        notificationChannel()
        setNotificationManager()
        buildPlayer()
        setNotification()
        networkRepository = NetworkManager.getInstance(scope)
        scope.launch {
            networkRepository?.currentTrack?.collect {
                withContext(Dispatchers.Main){
                    updateMediaSession(exoPlayer)
                }
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession.value
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }


    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
        _mediaSession.value?.release()
    }

    fun togglePlay(){
        if(exoPlayer.isPlaying){
            exoPlayer.pause()
        }else{
            exoPlayer.play()
        }
    }

    private fun buildPlayer(){
        audioProcessor = AudioProcessorWithCallback()
        val renderersFactory = CustomRenderersFactory(this, audioProcessor)
        exoPlayer = ExoPlayer.Builder(this, renderersFactory).build()
        exoPlayer
            .addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                updateNotification()
                _isPlaying.value = isPlaying
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                super.onPlaybackStateChanged(playbackState)
                when (playbackState) {
                    Player.STATE_IDLE -> {
                        Log.e("StateState", "STATE_IDLE")
                        setPlayer()
                    }
                    Player.STATE_BUFFERING -> {
                        Log.e("StateState", "STATE_BUFFERING")
                    }
                    Player.STATE_READY -> {
                        Log.e("StateState", "STATE_READY")
                        exoPlayer.play()
                    }
                    Player.STATE_ENDED -> {
                        Log.e("StateState", "STATE_ENDED")
                    }
                }
            }
        })
        setPlayer()
        setMediaSession(exoPlayer)
    }

    private fun setPlayer(){
        val imageUri = "android.resource://${packageName}/${R.drawable.radiomir}".toUri()
        val mediaItem = MediaItem.Builder()
            .setUri(Url.RadioMir.value)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setArtworkUri(imageUri)
                    .build()
            )
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(15000)
                    .setMinPlaybackSpeed(0.95f)
                    .setMaxPlaybackSpeed(1.05f)
                    .build()
            )
            .build()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    private fun updateMediaSession(player: ExoPlayer) {
        if (mediaSession.value != null) {
            val currentTrack = networkRepository?.currentTrack?.value
            val albumFileName = currentTrack?.album ?: ""
            val hasValidCover = albumFileName.isNotEmpty() && albumFileName != Url.AlbumEndPoint.value

            val safeArtworkUri = if (hasValidCover) {
                "${Url.AlbumEndPoint.value}$albumFileName".toUri()
            } else {
                "android.resource://$packageName/${R.drawable.radiomir}".toUri()
            }
            val metadata = MediaMetadata.Builder()
                .setTitle(currentTrack?.track ?: "Радио Мир")
                .setArtist(currentTrack?.artist ?: "Прямой эфир")
                .setArtworkUri(safeArtworkUri)
                .build()
            player.currentMediaItem?.let { currentItem ->
                val updated = currentItem.buildUpon()
                    .setMediaMetadata(metadata)
                    .build()
                player.replaceMediaItem(0, updated)
            }

            updateNotification()
        }
    }
    private fun setMediaSession(player: ExoPlayer){
        val forwardingPlayer = object : ForwardingPlayer(player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_STOP)
                    .build()
            }
            override fun seekToNext() {
                super.seekToNext()
                stopApp()
            }
        }
        val customStopCommand = SessionCommand(ACTION_STOP, Bundle.EMPTY)
        val stopButton = CommandButton.Builder()
            .setSessionCommand(customStopCommand)
            .setIconResId(android.R.drawable.btn_radio)
            .setDisplayName("Остановить")
            .setEnabled(true)
            .build()
        val session = MediaSession.Builder(this@RadioService, forwardingPlayer)
            .setCustomLayout(listOf(stopButton))
            .setMediaButtonPreferences(listOf(stopButton))
            .setCallback(object : MediaSession.Callback{
                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    if (Intent.ACTION_MEDIA_BUTTON == intent.action) {
                        val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        keyEvent?.let { event ->
                            if (event.action == KeyEvent.ACTION_DOWN) {
                                when (event.keyCode) {
                                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                        if(player.isPlaying){
                                            player.pause()
                                        }else{
                                            player.play()
                                        }
                                    }
                                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                        player.pause()
                                    }
                                    KeyEvent.KEYCODE_MEDIA_NEXT -> {

                                    }
                                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {

                                    }
                                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                                        player.stop()
                                    }
                                }
                            }
                        }
                        return true
                    }
                    return super.onMediaButtonEvent(session, controllerInfo, intent)
                }
            })
            .setId(Keywords.PlayerId.value)
        _mediaSession.value?.release()
        _mediaSession.value = session.build()
    }
    private fun stopApp(action: () -> Unit = {}){
        Handler(Looper.getMainLooper()).postDelayed({
            stopSelf()
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(0)
        }, 200)
        action()
    }
    private fun updateNotification() {
        setMediaSession(exoPlayer)
        notificationManager.notify(128, buildNotification())
    }
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this@RadioService, "128")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(R.drawable.baseline_radio_24)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(mediaSession.value!!)
            )
            .setOngoing(true)
            .build()
    }

    private fun notificationChannel(){
        val channel = NotificationChannel(
            "128",
            "Фоновый процесс для плееров",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun setNotification(){
        startForeground(128, buildNotification())
    }

    private fun setNotificationManager(){
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
}