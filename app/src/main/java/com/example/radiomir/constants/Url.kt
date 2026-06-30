package com.example.radiomir.constants

sealed class Url(val value: String){
    data object RadioMir: Url("https://cast.radiomir.by/hls/radiomir/aac_hifi.m3u8")
    data object TrackInfo: Url("https://api.radiomir.by/stream")
    data object AlbumEndPoint: Url("https://f.radiomir.by/radiomir/tracks/")
}