package com.example.radiomir.constants

sealed class Keywords (val value: String) {
    data object PlayerId: Keywords("player")
    data object CloseAction: Keywords("close_action")
}