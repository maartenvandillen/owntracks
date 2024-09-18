package it.vandillen.tracker.ui.waypoints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import it.vandillen.tracker.data.waypoints.WaypointModel
import it.vandillen.tracker.data.waypoints.WaypointsRepo
import it.vandillen.tracker.services.LocationProcessor

@HiltViewModel
class WaypointsViewModel
@Inject
constructor(waypointsRepo: WaypointsRepo, private val locationProcessor: LocationProcessor) :
    ViewModel() {

  val waypointsList: StateFlow<List<WaypointModel>> =
      waypointsRepo.allLive.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  fun exportWaypoints() {
    viewModelScope.launch { locationProcessor.publishWaypointsMessage() }
  }
}
