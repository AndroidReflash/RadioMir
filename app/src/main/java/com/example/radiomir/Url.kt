package com.example.radiomir

sealed class Url(val value: String){
    //up-to-date
    data object RadioMir: Url("https://icecast-mirtv.cdnvideo.ru/radio_mir_256")
}
