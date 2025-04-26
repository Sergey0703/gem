// app/src/main/java/com/example/gem/data/Word.kt

package com.example.gem.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "words")
data class Word(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val english: String,
    val russian: String,
    val transcription: String,
    val example: String,
    val dateAdded: Date = Date(),
    val lastUsed: Date? = null,
    val rating: Int = 0
)