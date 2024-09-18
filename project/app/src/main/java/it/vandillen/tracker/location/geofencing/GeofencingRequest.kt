package it.vandillen.tracker.location.geofencing

data class GeofencingRequest
constructor(val initialTrigger: Int? = null, val geofences: List<Geofence>? = null)
