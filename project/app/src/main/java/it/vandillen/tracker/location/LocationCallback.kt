package it.vandillen.tracker.location

interface LocationCallback {
  fun onLocationResult(locationResult: LocationResult)

  fun onLocationError()

  fun onLocationAvailability(locationAvailability: LocationAvailability)
}
