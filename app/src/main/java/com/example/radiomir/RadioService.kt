package com.example.radiomir

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import kotlin.system.exitProcess

class RadioService : Service() {
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var player: ExoPlayer

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setWakeMode(C.WAKE_MODE_NETWORK)
            playWhenReady = true
            setMediaItem(MediaItem.fromUri(Url.RadioMir.value))
            prepare()
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RadioService::WakeLock"
        )
        wakeLock.acquire()

        Log.d("RadioService", "Service Started")
        startForeground(128, createNotification())
    }

    //command activates when clicking on notification's button(s)/notification itself
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            "MY_ACTION" -> {
                val buttonStateIntent = Intent("com.example.UPDATE_PLAY_BUTTON")
                buttonStateIntent.putExtra(
                    "buttonState",
                    if (player.isPlaying) R.drawable.play else R.drawable.pause
                )
                sendBroadcast(buttonStateIntent)
                Log.d("RadioService", "MY_ACTION triggered")
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
            "STOP" ->{
                stopSelf()
                exitProcess(0)
            }
        }
        return START_STICKY
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun createNotification(): Notification {
        val channelId = "RadioChannel"
        val channel = NotificationChannel(
            channelId,
            "Radio Playback",
            NotificationManager.IMPORTANCE_HIGH
        )

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val actionIntent = Intent(this, RadioService::class.java).apply {
            action = "MY_ACTION"
        }

        val stopIntent = Intent(this, RadioService::class.java).apply {
            action = "STOP"
        }

        val actionPendingIntent = PendingIntent.getService(
            this,
            0,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.baseline_radio_24)
            .setContentIntent(actionPendingIntent)
            .addAction(R.drawable.pause, "STOP", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}