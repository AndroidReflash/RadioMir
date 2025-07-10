package com.example.radiomir

import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.example.radiomir.constants.Url
import com.example.radiomir.viewModel.RadioScreenViewModel
import com.radiomir.R
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun Interface(viewModel: RadioScreenViewModel){
    val isPlaying by viewModel.isPlaying.collectAsState()
    val equalizer by viewModel.equalizerArray.collectAsState()
    val brightness by viewModel.brightness.collectAsState()
    val trackInfo by viewModel.currentTrack.collectAsState()
    val picture by viewModel.picture.collectAsState()
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Background(brightness)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
        ) {
            TrackCover(picture)
            Spacer(Modifier.height(20.dp))
            MarqueeTrackTextBounce(
                "${trackInfo.artist} - ${trackInfo.track}",
                Modifier,
                fontSize = 30.sp,
                color = Color.Cyan.copy(alpha = 0.3f)
            )
            PlayButton(
                isPlaying
            ){
                viewModel.isPlaying()
            }
            AudioVisualizer(equalizer, Modifier.fillMaxWidth(), Color.Cyan.copy(alpha = 0.3f))
        }
    }
}

@Composable
fun PlayButton(
    isPlaying: Boolean,
    onPlay:()-> Unit
){
    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(RoundedCornerShape(100.dp))
            .clickable {
                onPlay()
            }
    ) {
        Image(
            painter = painterResource(id = if (isPlaying) R.drawable.pause else R.drawable.play),
            contentDescription = "radio",
            Modifier.fillMaxHeight(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun Background(brightness: Float) {

    val colorMatrix = ColorMatrix().apply {
        val scale = 1f + brightness
        setToScale(scale, scale, scale, 1f)
    }
    Image(
        painter = painterResource(id = R.drawable.radio), contentDescription = "radio",
        Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = ColorFilter.colorMatrix(colorMatrix)
    )
}

@Composable
fun AudioVisualizer(
    amplitudes: List<Double>,
    modifier: Modifier = Modifier,
    baseColor: Color
) {

    Canvas(modifier = modifier.size(width = 400.dp, height = 200.dp)) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val totalBands = 20
        val halfBands = totalBands / 2

        val gap = 4.dp.toPx()
        val bandWidth = (canvasWidth - (gap * (totalBands - 1))) / totalBands

        if (amplitudes.isNotEmpty()) {
            val step = amplitudes.size / halfBands
            val halfAmplitudes = List(halfBands) { index ->
                val sourceIndex = (index * step).coerceIn(0, amplitudes.lastIndex)
                amplitudes[sourceIndex]
            }
            val mirroredAmplitudes = halfAmplitudes.reversed() + halfAmplitudes

            mirroredAmplitudes.forEachIndexed { index, amplitude ->
                val finalAmplitude = amplitude.pow(0.5).toFloat()

                val minHeight = 6.dp.toPx()
                val animatedHeight = (finalAmplitude * canvasHeight).coerceIn(minHeight, canvasHeight)

                val xOffset = index * (bandWidth + gap)
                val yOffset = (canvasHeight - animatedHeight) / 2

                drawRoundRect(
                    color = baseColor,
                    topLeft = Offset(xOffset, yOffset),
                    size = Size(bandWidth, animatedHeight),
                    cornerRadius = CornerRadius(bandWidth / 2, bandWidth / 2)
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TrackCover(picture: String, modifier: Modifier = Modifier) {
    // Проверяем, валидный ли URL. Если нет — отдаем null, чтобы сработал error/fallback
    val isValidUrl = picture.isNotEmpty() && picture != Url.AlbumEndPoint.value
    val imageModel = if (isValidUrl) picture else null

    AsyncImage(
        model = imageModel,
        contentDescription = "Обложка трека",
        placeholder = painterResource(R.drawable.radiomir),
        error = painterResource(R.drawable.radiomir),
        fallback = painterResource(R.drawable.radiomir),
        modifier = modifier.size(200.dp).clip(RoundedCornerShape(100.dp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun MarqueeTrackTextBounce(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit,
    color: Color
) {
    val scrollSpeed = 50f // Пикселей в секунду
    var boxWidthPx by remember { mutableStateOf(0f) }
    val scroll = remember { Animatable(0f) }

    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        fontSize = fontSize,
        lineHeight = fontSize
    )

    val textLayoutResult = remember(text, fontSize) {
        textMeasurer.measure(AnnotatedString(text), textStyle)
    }
    val textWidthPx = textLayoutResult.size.width.toFloat()

    // Максимальное смещение — это то, насколько текст шире контейнера
    val maxScroll = (textWidthPx - boxWidthPx).coerceAtLeast(0f)
    val needsScroll = maxScroll > 0f

    LaunchedEffect(text, boxWidthPx) {
        if (needsScroll && boxWidthPx > 0f) {
            val duration = (maxScroll / scrollSpeed * 1000).toInt()

            while (true) {
                delay(2000) // Пауза перед началом движения

                // Едем влево (показываем правый край)
                scroll.animateTo(
                    targetValue = maxScroll,
                    animationSpec = tween(duration, easing = LinearOutSlowInEasing)
                )

                delay(2000) // Пауза на правом краю

                // Едем вправо (возвращаемся к началу)
                scroll.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(duration, easing = LinearOutSlowInEasing)
                )
            }
        } else {
            scroll.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onGloballyPositioned { coordinates ->
                boxWidthPx = coordinates.size.width.toFloat()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = textStyle,
            color = color,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .offset { IntOffset(-scroll.value.roundToInt(), 0) }
        )
    }
}