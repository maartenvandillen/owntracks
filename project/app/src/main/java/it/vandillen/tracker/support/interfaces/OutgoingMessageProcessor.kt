package it.vandillen.tracker.support.interfaces

import it.vandillen.tracker.net.ConnectionConfiguration

interface OutgoingMessageProcessor {
  fun activate()

  fun deactivate()

  @Throws(ConfigurationIncompleteException::class)
  fun getEndpointConfiguration(): ConnectionConfiguration
}
