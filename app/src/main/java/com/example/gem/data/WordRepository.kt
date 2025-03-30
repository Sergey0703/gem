package com.example.gem.data

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordRepository @Inject constructor(
    private val wordDao: WordDao,
    @ApplicationContext private val context: Context
) {
    suspend fun importWordsFromCsv(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).use { reader ->
                        val words = mutableListOf<Word>()
                        var line: String?
                        
                        // Пропускаем заголовок
                        reader.readLine()
                        
                        while (reader.readLine().also { line = it } != null) {
                            val parts = line?.split(",") ?: continue
                            if (parts.size >= 4) {
                                val word = Word(
                                    english = parts[0].trim().removeSurrounding("\""),
                                    russian = parts[1].trim().removeSurrounding("\""),
                                    transcription = parts[2].trim().removeSurrounding("\""),
                                    example = parts[3].trim().removeSurrounding("\""),
                                    dateAdded = Date(),
                                    lastUsed = null,
                                    rating = 0
                                )
                                words.add(word)
                            }
                        }
                        
                        if (words.isNotEmpty()) {
                            wordDao.insertWords(words)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }

    suspend fun updateWordUsage(wordId: Int) {
        withContext(Dispatchers.IO) {
            wordDao.updateWordUsage(wordId, Date())
        }
    }

    suspend fun decreaseWordRating(wordId: Int) {
        withContext(Dispatchers.IO) {
            wordDao.decreaseWordRating(wordId)
        }
    }
} 