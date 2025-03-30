package com.example.gem.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "words")
data class Word(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val english: String,
    val russian: String,
    val transcription: String = "",
    val example: String = "",
    val dateAdded: Date = Date(),
    val lastUsedDate: Date? = null,
    val successRate: Float = 0f
) 