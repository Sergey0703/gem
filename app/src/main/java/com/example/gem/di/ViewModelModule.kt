package com.example.gem.di

import com.example.gem.StoryViewModel
import com.example.gem.data.WordDao
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
    fun provideStoryViewModel(wordDao: WordDao): StoryViewModel {
        return StoryViewModel(wordDao)
    }
}