package it.vandillen.tracker.support.interfaces

interface StatefulServiceMessageProcessor {
  suspend fun reconnect(): Result<Unit>

  fun checkConnection(): Boolean
}
