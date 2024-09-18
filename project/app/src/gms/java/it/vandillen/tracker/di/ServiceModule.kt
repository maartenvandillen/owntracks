package it.vandillen.tracker.di

import android.app.Service
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.scopes.ServiceScoped
import it.vandillen.tracker.gms.location.geofencing.GMSGeofencingClient
import it.vandillen.tracker.location.geofencing.GeofencingClient

@InstallIn(ServiceComponent::class)
@Module
class ServiceModule {
  @Provides
  @ServiceScoped
  fun getGeofencingClient(service: Service): GeofencingClient = GMSGeofencingClient.create(service)
}
