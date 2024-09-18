package it.vandillen.tracker.gms.location

import it.vandillen.tracker.location.LatLng

fun LatLng.toGMSLatLng(): com.google.android.gms.maps.model.LatLng {
  return com.google.android.gms.maps.model.LatLng(latitude.value, longitude.value)
}
