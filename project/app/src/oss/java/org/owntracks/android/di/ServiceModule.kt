package it.vandillen.tracker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.scopes.ServiceScoped
import it.vandillen.tracker.location.NoopGeofencingClient
import it.vandillen.tracker.location.geofencing.GeofencingClient

@InstallIn(ServiceComponent::class)
@Module
class ServiceModule {
  @Provides @ServiceScoped fun getGeofencingClient(): GeofencingClient = NoopGeofencingClient()
}
