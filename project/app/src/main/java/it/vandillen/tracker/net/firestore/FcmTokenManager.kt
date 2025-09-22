package it.vandillen.tracker.net.firestore

import android.content.Context;

import com.google.firebase.messaging.FirebaseMessaging;

import it.vandillen.tracker.data.repos.EndpointStateRepo;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.launch
import timber.log.Timber;

class FcmTokenManager(
  private val context:Context,
  private val scope:CoroutineScope,
  private val endpointStateRepo:EndpointStateRepo
) {
  private val prefs = context.getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
  private val TOKEN_KEY = "fcm_token"

  private var storedToken: String?
    get() = prefs.getString(TOKEN_KEY, null)
    set(value) = prefs.edit().putString(TOKEN_KEY, value).apply()

  fun getCachedToken(): String? = storedToken

  fun refreshToken(): com.google.android.gms.tasks.Task<String> {
    val task = FirebaseMessaging.getInstance().token
    task.addOnCompleteListener { task ->
      if (task.isSuccessful) {
        val newToken = task.result
        if (!newToken.isNullOrEmpty()) {
          if (newToken != storedToken) {
            storedToken = newToken
          }
          scope.launch { endpointStateRepo.firestoreFcmToken.emit(newToken) }
          Timber.d("FCM token refreshed: $newToken")
        } else {
          Timber.w("Empty FCM token received")
        }
      } else {
        Timber.w(task.exception, "Failed to refresh FCM token")
      }
    }
    return task
  }

  fun updateTokenFromService(token: String) {
    storedToken = token
    scope.launch { endpointStateRepo.firestoreFcmToken.emit(token) }
  }
}
