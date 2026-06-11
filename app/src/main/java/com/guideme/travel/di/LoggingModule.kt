package com.guideme.travel.di

import com.guideme.travel.data.logging.AndroidGuideMeLogger
import com.guideme.travel.domain.logging.GuideMeLogger
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingModule {
    @Binds
    @Singleton
    abstract fun bindGuideMeLogger(impl: AndroidGuideMeLogger): GuideMeLogger
}
