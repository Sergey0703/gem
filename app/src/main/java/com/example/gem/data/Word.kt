package com.example.gem.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class Word(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val english: String,
    val russian: String,
    val transcription: String,
    val example: String
) 