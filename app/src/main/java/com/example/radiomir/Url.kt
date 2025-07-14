package com.example.radiomir

sealed class Url(val value: String){
    //up-to-date
    data object RadioMir: Url("https://media1.datacenter.by:1936/radiomir/radiomir/playlist.m3u8")
}
