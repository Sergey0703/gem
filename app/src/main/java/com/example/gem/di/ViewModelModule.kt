// app/src/main/java/com/example/gem/di/ViewModelModule.kt

package com.example.gem.di

import com.example.gem.StoryViewModel
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
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object ViewModelModule {

    @Provides
    @ViewModelScoped
    fun provideStoryViewModel(
        wordDao: WordDao,
        generateStoryUseCase: GenerateStoryUseCase,
        textToSpeechUseCase: TextToSpeechUseCase,
        translateStoryUseCase: TranslateStoryUseCase,
        wordInfoUseCase: WordInfoUseCase,
        wordStorageUseCase: WordStorageUseCase
    ): StoryViewModel {
        return StoryViewModel(
            wordDao,
            generateStoryUseCase,
            textToSpeechUseCase,
            translateStoryUseCase,
            wordInfoUseCase,
            wordStorageUseCase
        )
    }
}