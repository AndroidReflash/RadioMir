package com.example.radiomir

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Preview
@Composable
fun Interface(){
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Background()
        PlayButton()
    }
}

@Composable
fun PlayButton(){
    val context = LocalContext.current
    var buttonState by remember {
        mutableIntStateOf(R.drawable.pause)
    }

    //value for button's updating from service notification
    val broadcastReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val newState = intent.getIntExtra("buttonState", R.drawable.pause)
                buttonState = newState
            }
        }
    }

    DisposableEffect(context) {
        val filter = IntentFilter("com.example.UPDATE_PLAY_BUTTON")
        context.registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        onDispose {
            context.unregisterReceiver(broadcastReceiver)
        }
    }

    Box(
        modifier = Modifier
            .height(200.dp)
            .width(200.dp)
            .clickable {
                val intent = Intent(context, RadioService::class.java).apply {
                    action = "MY_ACTION"
                }
                ContextCompat.startForegroundService(context, intent)
            }
    ) {
        Image(
            painter = painterResource(id = buttonState), contentDescription = "radio",
            Modifier.fillMaxHeight(), contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun Background() {
    Image(
        painter = painterResource(id = R.drawable.radio), contentDescription = "radio",
        Modifier.fillMaxHeight(), contentScale = ContentScale.Crop
    )
}
