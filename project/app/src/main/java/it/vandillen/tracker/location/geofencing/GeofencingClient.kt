package it.vandillen.tracker.location.geofencing

import android.content.Context

interface GeofencingClient {
  fun removeGeofences(context: Context)

  fun addGeofences(request: GeofencingRequest, context: Context)
}
