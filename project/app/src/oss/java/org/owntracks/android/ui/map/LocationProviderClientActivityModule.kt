package it.vandillen.tracker.ui.map

import android.app.Activity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped
import it.vandillen.tracker.location.AospLocationProviderClient
import it.vandillen.tracker.location.LocationProviderClient

@InstallIn(ActivityComponent::class)
@Module
class LocationProviderClientActivityModule {
  @Provides
  @ActivityScoped
  fun getLocationProviderClient(activity: Activity): LocationProviderClient =
      AospLocationProviderClient(activity)
}
