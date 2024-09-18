package it.vandillen.tracker.net.http

import okhttp3.HttpUrl.Companion.toHttpUrl
import it.vandillen.tracker.net.ConnectionConfiguration
import it.vandillen.tracker.support.interfaces.ConfigurationIncompleteException

data class HttpConfiguration(
    val url: String,
    val username: String,
    val password: String,
    val deviceId: String
) : ConnectionConfiguration {
  override fun validate() {
    try {
      url.toHttpUrl()
    } catch (e: IllegalArgumentException) {
      throw ConfigurationIncompleteException(e)
    }
  }
}
