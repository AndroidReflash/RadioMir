package com.example.radiomir.repository

import android.annotation.SuppressLint
import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.example.radiomir.constants.Url
import com.example.radiomir.dataclasses.TrackInfoDTO
import com.example.radiomir.repository.`interface`.NetworkRepository
import com.radiomir.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.net.SocketTimeoutException
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class NetworkRepositoryImpl(
    private val context: Context,
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

    private suspend fun setConnection(context: Context, link: String): HttpsURLConnection {
        val url = URL(link)
        val connection = withContext(Dispatchers.IO) {
            url.openConnection()
        } as HttpsURLConnection
        connection.sslSocketFactory = getCustomSslSocketFactory(context)
        connection.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        )
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.requestMethod = "GET"
        return connection
    }

    override fun getCustomSslOkHttpClient(): OkHttpClient {
        val cf = CertificateFactory.getInstance("X.509")
        val ca = context.resources.openRawResource(R.raw.radiomir_cert).use { inputStream ->
            cf.generateCertificate(inputStream) as X509Certificate
        }

        val keyStoreType = KeyStore.getDefaultType()
        val keyStore = KeyStore.getInstance(keyStoreType).apply {
            load(null, null)
            setCertificateEntry("ca", ca)
        }

        val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
        val tmf = TrustManagerFactory.getInstance(tmfAlgorithm).apply {
            init(keyStore)
        }

        val trustManagers = tmf.trustManagers
        val x509TrustManager = trustManagers[0] as X509TrustManager

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustManagers, null)
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, x509TrustManager)
            .build()
    }

    private fun getCustomSslSocketFactory(context: Context): SSLSocketFactory {
        val cf = CertificateFactory.getInstance("X.509")
        val ca = context.resources.openRawResource(R.raw.radiomir_cert).use { inputStream ->
            cf.generateCertificate(inputStream) as X509Certificate
        }

        val keyStoreType = KeyStore.getDefaultType()
        val keyStore = KeyStore.getInstance(keyStoreType).apply {
            load(null, null)
            setCertificateEntry("radiomir_ca", ca)
        }

        val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
        val tmf = TrustManagerFactory.getInstance(tmfAlgorithm).apply {
            init(keyStore)
        }

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, tmf.trustManagers, null)
        }

        return sslContext.socketFactory
    }

    private suspend fun getDataCurrentTrack(){
        val connection = setConnection(context, Url.TrackInfo.value)
        try {
            withContext(Dispatchers.IO) { connection.connect() }
            val response = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            if (response.isEmpty() || response == "null" || response == "[]") {
                return
            }
            val currentTrack = try {
                Json.decodeFromString(response)
            } catch (e: Exception) {
                TrackInfoDTO()
            }
            _currentTrack.value = TrackInfoDTO(
                artist = currentTrack.artist,
                album = Url.AlbumEndPoint.value + currentTrack.album,
                track = currentTrack.track,
                isNews = currentTrack.isNews
            )
        } catch (e: SocketTimeoutException) {

        } catch (e: Exception) {

        }
    }
}

@UnstableApi
object NetworkManager {
    @SuppressLint("StaticFieldLeak")
    @Volatile private var instance: NetworkRepository? = null

    fun getInstance(
        context: Context,
        scope: CoroutineScope
    ): NetworkRepository {
        return instance ?: synchronized(this) {
            instance ?: NetworkRepositoryImpl(context, scope).also {
                instance = it
            }
        }
    }
}