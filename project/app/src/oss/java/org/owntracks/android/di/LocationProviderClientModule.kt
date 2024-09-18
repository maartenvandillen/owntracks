package it.vandillen.tracker.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import it.vandillen.tracker.location.AospLocationProviderClient
import it.vandillen.tracker.location.LocationProviderClient

@InstallIn(SingletonComponent::class)
@Module
class LocationProviderClientModule {
  @Provides
  @Singleton
  fun getLocationProviderClient(
      @ApplicationContext applicationContext: Context
  ): LocationProviderClient = AospLocationProviderClient(applicationContext)
}
