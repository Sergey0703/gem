// app/src/main/java/com/example/gem/di/DomainModule.kt

package com.example.gem.di

import android.content.Context
import com.example.gem.data.WordDao
import com.example.gem.domain.GenerateStoryUseCase
import com.example.gem.domain.TextToSpeechUseCase
import com.example.gem.domain.TranslateStoryUseCase
import com.example.gem.domain.WordInfoUseCase
import com.example.gem.domain.WordStorageUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object DomainModule {

    @Provides
    @ViewModelScoped
    fun provideTextToSpeechUseCase(): TextToSpeechUseCase {
        return TextToSpeechUseCase()
    }

    @Provides
    @ViewModelScoped
    fun provideWordStorageUseCase(wordDao: WordDao): WordStorageUseCase {
        return WordStorageUseCase(wordDao)
    }

    @Provides
    @ViewModelScoped
    fun provideGenerateStoryUseCase(
        wordStorageUseCase: WordStorageUseCase,
        @ApplicationContext context: Context
    ): GenerateStoryUseCase {
        return GenerateStoryUseCase(wordStorageUseCase, context)
    }

    @Provides
    @ViewModelScoped
    fun provideTranslateStoryUseCase(): TranslateStoryUseCase {
        return TranslateStoryUseCase()
    }

    @Provides
    @ViewModelScoped
    fun provideWordInfoUseCase(): WordInfoUseCase {
        return WordInfoUseCase()
    }
}