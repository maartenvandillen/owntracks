package it.vandillen.tracker.net.firestore

import it.vandillen.tracker.net.ConnectionConfiguration
import it.vandillen.tracker.support.interfaces.ConfigurationIncompleteException

data class FirestoreConfiguration(
    val url: String,
    val username: String,
    val password: String,
    val deviceId: String
) : ConnectionConfiguration {
  override fun validate() {
    try {
      //TODO: add validation logic
//      url.toHttpUrl()
    } catch (e: IllegalArgumentException) {
      throw ConfigurationIncompleteException(e)
    }
  }
}
