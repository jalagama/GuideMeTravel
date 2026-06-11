package com.guideme.travel.di

import com.guideme.travel.data.analytics.FirebaseGuideMeAnalytics
import com.guideme.travel.domain.analytics.GuideMeAnalytics
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds
    @Singleton
    abstract fun bindGuideMeAnalytics(impl: FirebaseGuideMeAnalytics): GuideMeAnalytics
}
