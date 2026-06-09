package com.guideme.travel.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.guideme.travel.data.auth.AuthRepositoryImpl
import com.guideme.travel.data.local.GuideMeDatabase
import com.guideme.travel.data.location.LocationRepositoryImpl
import com.guideme.travel.data.preferences.PreferencesRepositoryImpl
import com.guideme.travel.data.repository.CuratedContentRepositoryImpl
import com.guideme.travel.data.repository.GuideRepositoryImpl
import com.guideme.travel.data.repository.TripRepositoryImpl
import com.guideme.travel.data.repository.UserRepositoryImpl
import com.guideme.travel.data.work.DownloadWorkRepositoryImpl
import com.guideme.travel.domain.repository.AuthRepository
import com.guideme.travel.domain.repository.CuratedContentRepository
import com.guideme.travel.domain.repository.DownloadWorkRepository
import com.guideme.travel.domain.repository.GuideRepository
import com.guideme.travel.domain.repository.LocationRepository
import com.guideme.travel.domain.repository.PreferencesRepository
import com.guideme.travel.domain.repository.TripRepository
import com.guideme.travel.domain.repository.UserRepository
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
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

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

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindDownloadWorkRepository(impl: DownloadWorkRepositoryImpl): DownloadWorkRepository

    @Binds
    @Singleton
    abstract fun bindCuratedContentRepository(impl: CuratedContentRepositoryImpl): CuratedContentRepository
}
