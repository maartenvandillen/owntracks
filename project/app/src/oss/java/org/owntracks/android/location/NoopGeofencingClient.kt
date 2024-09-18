package it.vandillen.tracker.location

import android.content.Context
import it.vandillen.tracker.location.geofencing.GeofencingClient
import it.vandillen.tracker.location.geofencing.GeofencingRequest

class NoopGeofencingClient : GeofencingClient {
  override fun removeGeofences(context: Context) {}

  override fun addGeofences(request: GeofencingRequest, context: Context) {}
}
