package com.example.radiomir

sealed class Url(val value: String){
    data object RadioMir: Url("https://stream2.datacenter.by/radiomir")
}
