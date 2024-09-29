package it.vandillen.tracker.net.firestore

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
  private var _fcmToken: String = "UNKNOWN"

  override fun activate() {
    Timber.d("FirestoreMessageProcessorEndpoint activated")
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
        val data = hashMapOf(
            "fcmToken" to _fcmToken,
            "lastUpdate" to Calendar.getInstance().timeInMillis,
            "name" to message.ssid,                               //device id passed in via ssid field from LocationProcessor.publishLocationMessage()
            "locationTimestamp" to message.timestamp * 1000,
            "latitude" to message.latitude,
            "longitude" to message.longitude,
            "battery" to message.battery,
            "accuracy" to message.accuracy,
            "altitude" to message.altitude,
            "bearing" to message.bearing,
            "speed" to message.velocity
        )
        val docRef = firestore.collection("trackers").document(_fcmToken)
        docRef.set(data, SetOptions.merge()).await()

        Timber.d("Message sent to Firestore successfully: $message")
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
