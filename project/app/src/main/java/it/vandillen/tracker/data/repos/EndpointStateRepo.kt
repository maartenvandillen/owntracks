package it.vandillen.tracker.data.repos

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import it.vandillen.tracker.data.EndpointState
import timber.log.Timber

@Singleton
class EndpointStateRepo @Inject constructor() {

  val endpointState: MutableStateFlow<EndpointState> = MutableStateFlow(EndpointState.IDLE)

  val endpointQueueLength: MutableStateFlow<Int> = MutableStateFlow(0)

  val serviceStartedDate: MutableStateFlow<Instant> = MutableStateFlow(Instant.now())

  // Firestore-specific runtime info exposed for UI
  val firestoreFcmToken: MutableStateFlow<String> = MutableStateFlow("UNKNOWN-FCM")
  val firestoreUniqueId: MutableStateFlow<String> = MutableStateFlow("UNKNOWN-ID")
  val firestoreLastSentMillis: MutableStateFlow<Long?> = MutableStateFlow(null)

  suspend fun setState(newEndpointState: EndpointState) {
    Timber.v(
        "Setting endpoint state $newEndpointState called from: ${
            Thread.currentThread().stackTrace[3].run {
                "$className: $methodName"
            }
            }")
    endpointState.emit(newEndpointState)
  }

  suspend fun setQueueLength(queueLength: Int) {
    Timber.v("Setting queuelength=$queueLength")
    endpointQueueLength.emit(queueLength)
  }

  suspend fun setServiceStartedNow() {
    serviceStartedDate.emit(Instant.now())
  }
}
