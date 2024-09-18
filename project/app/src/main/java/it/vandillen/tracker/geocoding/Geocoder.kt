package it.vandillen.tracker.geocoding

import it.vandillen.tracker.location.LatLng

internal interface Geocoder {
  suspend fun reverse(latLng: LatLng): GeocodeResult
}
