package com.guideme.travel.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.guideme.travel.data.local.GuideMeDatabase
import com.guideme.travel.data.repository.GuideRepositoryImpl
import com.guideme.travel.data.repository.TripRepositoryImpl
import com.guideme.travel.domain.repository.GuideRepository
import com.guideme.travel.domain.repository.TripRepository
import com.guideme.travel.service.AudioGuidePlayer
import dagger.Binds
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
    fun provideDatabase(@ApplicationContext context: Context): GuideMeDatabase {
        return Room.databaseBuilder(
            context,
            GuideMeDatabase::class.java,
            "guideme.db"
        ).build()
    }

    @Provides
    fun provideTripDao(database: GuideMeDatabase) = database.tripDao()

    @Provides
    fun provideAttractionDao(database: GuideMeDatabase) = database.attractionDao()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions {
        return FirebaseFunctions.getInstance("asia-south1")
    }

    @Provides
    @Singleton
    fun provideAudioGuidePlayer(@ApplicationContext context: Context): AudioGuidePlayer {
        return AudioGuidePlayer(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTripRepository(impl: TripRepositoryImpl): TripRepository

    @Binds
    @Singleton
    abstract fun bindGuideRepository(impl: GuideRepositoryImpl): GuideRepository
}
