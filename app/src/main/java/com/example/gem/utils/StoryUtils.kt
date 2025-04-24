package com.example.gem.utils

// Расширение для Char для определения знаков пунктуации
fun Char.isPunctuation(): Boolean {
    return this in ".,;:!?\"'()[]{}<>«»—–-…/\\&@#$%^*_=+`~|"
}

// Массив слов для примера (если он использовался)
val words = arrayOf(
    "adventure",
    "mystery",
    "rainbow",
    "dragon",
    "treasure",
    "magic",
    "journey",
    "forest",
    "castle",
    "ocean"
)