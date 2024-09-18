package it.vandillen.tracker.net.firestore

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.tasks.await
import it.vandillen.tracker.model.messages.MessageBase
import it.vandillen.tracker.model.Parser
import it.vandillen.tracker.net.MessageProcessorEndpoint
import it.vandillen.tracker.data.repos.EndpointStateRepo
import it.vandillen.tracker.net.ConnectionConfiguration
import it.vandillen.tracker.preferences.Preferences
import it.vandillen.tracker.preferences.types.ConnectionMode
import it.vandillen.tracker.services.MessageProcessor
import timber.log.Timber
import java.security.KeyStore

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

  override fun activate() {
    Timber.d("FirestoreMessageProcessorEndpoint activated")
    // You can perform any initialization required when this endpoint is activated
  }

  override suspend fun sendMessage(message: MessageBase): Result<Unit> {
    return try {
      val messageData = parser.toJson(message)
      firestore.collection("messages")
          .add(messageData)
          .await()

      Timber.d("Message sent to Firestore successfully: $message")
      Result.success(Unit)
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
