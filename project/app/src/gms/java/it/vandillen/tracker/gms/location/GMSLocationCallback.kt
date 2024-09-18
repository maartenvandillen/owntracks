package it.vandillen.tracker.gms.location

import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationResult
import it.vandillen.tracker.location.LocationCallback

/**
 * This is a wrapper around a [LocationCallback] instance that can be given to something that needs
 * a [com.google.android.gms.location.LocationCallback]. Once the thing that owns the
 * [com.google.android.gms.location.LocationCallback] has any of its methods triggered, it then
 * passes that on to the methods of the [LocationCallback]
 *
 * @property clientCallBack the [LocationCallback] to wrap
 */
class GMSLocationCallback(private val clientCallBack: LocationCallback) :
    com.google.android.gms.location.LocationCallback() {
  override fun onLocationResult(locationResult: LocationResult) {
    super.onLocationResult(locationResult)
    locationResult.lastLocation?.apply {
      clientCallBack.onLocationResult(it.vandillen.tracker.location.LocationResult(this))
    } ?: run { clientCallBack.onLocationError() }
  }

  override fun onLocationAvailability(locationAvailability: LocationAvailability) {
    super.onLocationAvailability(locationAvailability)
    clientCallBack.onLocationAvailability(
        it.vandillen.tracker.location.LocationAvailability(
            locationAvailability.isLocationAvailable))
  }
}
