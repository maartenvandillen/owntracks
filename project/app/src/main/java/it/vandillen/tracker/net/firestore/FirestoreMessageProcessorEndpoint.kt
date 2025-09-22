package it.vandillen.tracker.net.firestore

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import android.content.SharedPreferences
import it.vandillen.tracker.model.messages.MessageBase
import it.vandillen.tracker.model.Parser
import it.vandillen.tracker.net.MessageProcessorEndpoint
import it.vandillen.tracker.data.repos.EndpointStateRepo
import it.vandillen.tracker.model.messages.MessageLocation
import it.vandillen.tracker.net.ConnectionConfiguration
import it.vandillen.tracker.preferences.Preferences
import it.vandillen.tracker.preferences.types.ConnectionMode
import it.vandillen.tracker.services.MessageProcessor
import kotlinx.coroutines.delay
import timber.log.Timber
import java.security.KeyStore
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import it.vandillen.tracker.net.firestore.FcmTokenManager
import it.vandillen.tracker.data.EndpointState

class FirestoreMessageProcessorEndpoint(
  messageProcessor: MessageProcessor,
  private val parser: Parser,
  private val preferences: Preferences,
  private val context: Context,
  private val endpointStateRepo: EndpointStateRepo,
  private val caKeyStore: KeyStore,
  private val scope: CoroutineScope,
  private val ioDispatcher: CoroutineDispatcher,
) : MessageProcessorEndpoint(messageProcessor) {

  private val fcmTokenManager = FcmTokenManager(context, scope, endpointStateRepo)

  override fun onFinalizeMessage(message: MessageBase): MessageBase = message

  override val modeId: ConnectionMode = ConnectionMode.FIRESTORE

  private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
  private var _fcmToken: String = fcmTokenManager.getCachedToken() ?: "UNKNOWN-FCM"
  private var _uniqueId: String = "UNKNOWN-ID"

  private var _appVersion: String = "N/A"
  private var tokenJob: Job? = null

  private fun PackageManager.getPackageInfoCompat(packageName: String): PackageInfo =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
      } else {
        @Suppress("DEPRECATION") getPackageInfo(packageName, 0)
      }

  override fun activate() {
    Timber.d("FirestoreMessageProcessorEndpoint activated")
    _appVersion = context
            .packageManager
            .getPackageInfoCompat(context.packageName)
            .versionName ?: "N/A"

    //since fcm token can change over time we use a more stable id for communication
    FirebaseInstallations.getInstance().id.addOnCompleteListener { task ->
      if (task.isSuccessful) {
        _uniqueId = task.result
        scope.launch { endpointStateRepo.firestoreUniqueId.emit(_uniqueId) }
      } else {
        Timber.e(task.exception, "Error getting Firebase Installation ID")
      }
    }

    // Try cached FCM token first; refresh asynchronously
    fcmTokenManager.getCachedToken()?.let { cached ->
      _fcmToken = cached
      scope.launch { endpointStateRepo.firestoreFcmToken.emit(cached) }
    }

    // Subscribe to token updates so this endpoint always uses the latest value
    tokenJob?.cancel()
    tokenJob = scope.launch(ioDispatcher) {
      endpointStateRepo.firestoreFcmToken.collectLatest { token ->
        if (token.isNotBlank()) {
          _fcmToken = token
          Timber.d("FCM token updated in endpoint: ${token.take(16)}…")
        }
      }
    }

    // Always attempt a background refresh to keep it current
    fcmTokenManager.refreshToken()

    // Firestore is stateless; once activated, consider endpoint ready/idle so the UI moves off INITIAL
    scope.launch { endpointStateRepo.setState(EndpointState.IDLE) }
  }

  override suspend fun sendMessage(message: MessageBase): Result<Unit> {
    return try {
      if (message is MessageLocation) {
        if (_uniqueId != "UNKNOWN-ID") {
          val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
          val currentDate = Date()
          val currantDateString = dateFormat.format(currentDate)

          val name = message.ssid         //device id passed in via ssid field from LocationProcessor.publishLocationMessage()
          val data = hashMapOf(
              "fcmToken" to _fcmToken,
              "appVersion" to _appVersion,
              "lastUpdate" to Calendar.getInstance().timeInMillis,
              "lastUpdateString" to currantDateString,
              "name" to name,
              "locationTimestamp" to message.timestamp * 1000,
              "latitude" to message.latitude,
              "longitude" to message.longitude,
              "battery" to message.battery,
              "accuracy" to message.accuracy,
              "altitude" to message.altitude,
              "bearing" to message.bearing,
              "speed" to message.velocity
          )

          val tenant = message.trackerId?.uppercase() ?: "NEW"
          if (tenant != "") {
            scope.launch { endpointStateRepo.setState(EndpointState.CONNECTING) }
            val docRef = firestore.collection("tenants/" + tenant + "/trackers").document("$name-$_uniqueId")
            docRef.set(data, SetOptions.merge()).await()

            // expose last message sent timestamp for UI overlay
            scope.launch {
              endpointStateRepo.firestoreLastSentMillis.emit(System.currentTimeMillis())
              endpointStateRepo.setState(EndpointState.IDLE)
            }
            Timber.d("Message sent to Firestore successfully: $message")
            Result.success(Unit)
          } else {
            Timber.d("Message NOT sent to Firestore (no tenant)")
            scope.launch { endpointStateRepo.setState(EndpointState.ERROR.withMessage("No tenant")) }
            Result.failure<Unit>(Exception("Message NOT sent to Firestore (no tenant)"))
          }
        } else {
          Timber.d("Message NOT sent to Firestore (no unique device id)")
          scope.launch { endpointStateRepo.setState(EndpointState.ERROR.withMessage("No unique device id")) }
          Result.failure<Unit>(Exception("Message NOT sent to Firestore (no unique device id)"))
        }
      } else {
        Timber.d("Message was not a MessageLocation instance: $message")
        scope.launch { endpointStateRepo.setState(EndpointState.ERROR.withMessage("Invalid message type")) }
        Result.failure(Exception("Message was not a MessageLocation instance"))
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to send message to Firestore")
      scope.launch { endpointStateRepo.setState(EndpointState.ERROR.withError(e)) }
      Result.failure(e)
    }
  }

  override fun deactivate() {
    Timber.d("FirestoreMessageProcessorEndpoint deactivated")
    tokenJob?.cancel()
    tokenJob = null
    // Clean up resources if needed
  }

  override fun getEndpointConfiguration(): ConnectionConfiguration {
    TODO("Not yet implemented")
  }
}
