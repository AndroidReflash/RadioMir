package com.example.radiomir.constants

sealed class Url(val value: String){
    data object RadioMir: Url("https://media1.datacenter.by:1936/radiomir/radiomir/playlist.m3u8")
    data object TrackInfo: Url("https://api.radiomir.by/stream")
    data object AlbumEndPoint: Url("https://f.radiomir.by/radiomir/tracks/")
}