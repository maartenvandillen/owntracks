package it.vandillen.tracker.data.waypoints

import android.location.Location
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import it.vandillen.tracker.location.geofencing.Geofence
import it.vandillen.tracker.location.geofencing.Latitude
import it.vandillen.tracker.location.geofencing.Longitude

@Entity(indices = [Index(value = ["tst"], unique = true)])
data class WaypointModel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    var description: String = "",
    var geofenceLatitude: Latitude = Latitude(0.0),
    var geofenceLongitude: Longitude = Longitude(0.0),
    var geofenceRadius: Int = 0,
    var lastTriggered: Instant? = null,
    var lastTransition: Int = 0,
    val tst: Instant = Instant.now()
) {
  fun getLocation(): Location =
      Location("waypoint").apply {
        latitude = geofenceLatitude.value
        longitude = geofenceLongitude.value
        accuracy = geofenceRadius.toFloat()
      }

  fun isUnknown(): Boolean = lastTransition == Geofence.GEOFENCE_TRANSITION_UNKNOWN
}
