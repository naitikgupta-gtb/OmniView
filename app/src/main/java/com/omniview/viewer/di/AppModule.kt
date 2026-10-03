package com.omniview.viewer.di

import android.content.Context
import androidx.room.Room
import com.omniview.viewer.data.local.AppDatabase
import com.omniview.viewer.data.local.FavoriteDao
import com.omniview.viewer.data.local.RecentDao
import com.omniview.viewer.data.local.SettingsDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "omniview.db")
            .fallbackToDestructiveMigration() // dev-time safety net: schema changes reset local data instead of crashing
            .build()

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideRecentDao(db: AppDatabase): RecentDao = db.recentDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore =
        SettingsDataStore(context)
}
