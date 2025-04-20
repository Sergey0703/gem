package com.example.gem.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY dateAdded DESC")
    fun getAllWords(): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE english LIKE :searchQuery OR russian LIKE :searchQuery ORDER BY dateAdded DESC")
    fun searchWords(searchQuery: String): Flow<List<Word>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: Word)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<Word>)

    @Update
    suspend fun updateWord(word: Word)

    @Delete
    suspend fun deleteWord(word: Word)

    @Query("UPDATE words SET lastUsed = :date, rating = rating + 1 WHERE id = :wordId")
    suspend fun updateWordUsage(wordId: Int, date: Date)

    @Query("UPDATE words SET rating = rating - 1 WHERE id = :wordId")
    suspend fun decreaseWordRating(wordId: Int)

    @Query("SELECT * FROM words ORDER BY rating DESC LIMIT :limit")
    fun getTopWords(limit: Int): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE lastUsed IS NULL OR lastUsed < :date ORDER BY lastUsed ASC, RANDOM() LIMIT :limit")
    fun getWordsToReview(date: Date, limit: Int): Flow<List<Word>>
}