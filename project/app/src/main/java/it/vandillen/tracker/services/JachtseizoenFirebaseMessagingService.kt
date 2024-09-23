package it.vandillen.tracker.services

import android.app.PendingIntent
import android.content.Intent
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class JachtseizoenFirebaseMessagingService : FirebaseMessagingService() {

  /**
     * Called if the FCM registration token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the
     * FCM registration token is initially generated so this is where you would retrieve the token.
     */
    override fun onNewToken(token: String) {
      Timber.d("Refreshed token: $token")
//        TODO: sendRegistrationToServer(token)
    }

  override fun onMessageReceived(remoteMessage: RemoteMessage) {
    // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
    Timber.d("From: ${remoteMessage.from}")

    // Check if message contains a data payload.
    val data = remoteMessage.data
    if (data.isNotEmpty()) {
      Timber.d("Message data payload: ${data}")

      val priority = if (remoteMessage.priority == 1) "high" else "normal"
      Timber.d("Priority = ${remoteMessage.priority} = ${priority}")

      val mt = data.get("messageType")
      val messageType = MessageType.fromString(mt)
      val message = MessageModel(messageType)
      message.priority = priority

      when (messageType) {
        MessageType.LOCATION_REQUEST -> {
          PendingIntent.getService(
              applicationContext,
              0,
              Intent().setAction(BackgroundService.INTENT_ACTION_SEND_LOCATION_USER),
              BackgroundService.UPDATE_CURRENT_INTENT_FLAGS).send()
        }
        else -> { }
      }
    }
  }
}
