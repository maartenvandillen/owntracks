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
import kotlinx.coroutines.tasks.await
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
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.KeyStore
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

  override fun onFinalizeMessage(message: MessageBase): MessageBase = message

  override val modeId: ConnectionMode = ConnectionMode.FIRESTORE

  private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
  private var _fcmToken: String = "UNKNOWN-FCM"
  private var _uniqueId: String = "UNKNOWN-ID"

  private var _appVersion: String = "N/A"

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
            .versionName

    //since fcm token can change over time we use a more stable id for communication
    FirebaseInstallations.getInstance().id.addOnCompleteListener { task ->
      if (task.isSuccessful) {
        _uniqueId = task.result
      } else {
        Timber.e(task.exception, "Error getting Firebase Installation ID")
      }
    }

    FirebaseMessaging.getInstance().token.addOnCompleteListener(
      OnCompleteListener { task ->
        if (!task.isSuccessful) {
          scope.launch {
            delay(2000)
            FirebaseMessaging.getInstance().token.addOnCompleteListener(
                OnCompleteListener OnCompleteListener2@{ task2 ->
                  if (!task.isSuccessful) {
                    return@OnCompleteListener2
                  }
                  _fcmToken = task2.result
                }
            )
          }

          return@OnCompleteListener
        }

        // Get new FCM registration token
        _fcmToken = task.result
      }
    )
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
          val docRef = firestore.collection("tenants/" + tenant + "/trackers").document("$name-$_uniqueId")
          docRef.set(data, SetOptions.merge()).await()

          Timber.d("Message sent to Firestore successfully: $message")
        } else {
          Timber.d("Message NOT sent to Firestore (no unique device id)")
        }
        Result.success(Unit)
      } else {
        Timber.d("Message was not a MessageLocation instance: $message")
        Result.failure(Exception("Message was not a MessageLocation instance"))
      }
    } catch (e: Exception) {
      Timber.e(e, "Failed to send message to Firestore")
      Result.failure(e)
    }
  }

  override fun deactivate() {
    Timber.d("FirestoreMessageProcessorEndpoint deactivated")
    // Clean up resources if needed
  }

  override fun getEndpointConfiguration(): ConnectionConfiguration {
    TODO("Not yet implemented")
  }

//  override fun getEndpointConfiguration(): ConnectionConfiguration {
//    // You can implement any logic to fetch and return the endpoint configuration
//    Timber.d("Fetching Firestore endpoint configuration")
//  }
}
