package it.vandillen.tracker.geocoding

import it.vandillen.tracker.location.LatLng

class GeocoderNone internal constructor() : Geocoder {
  override suspend fun reverse(latLng: LatLng): GeocodeResult {
    return GeocodeResult.Empty
  }
}
