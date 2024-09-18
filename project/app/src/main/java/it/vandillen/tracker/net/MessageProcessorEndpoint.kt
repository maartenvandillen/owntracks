package it.vandillen.tracker.net

import it.vandillen.tracker.model.messages.MessageBase
import it.vandillen.tracker.preferences.types.ConnectionMode
import it.vandillen.tracker.services.MessageProcessor
import it.vandillen.tracker.support.interfaces.OutgoingMessageProcessor

abstract class MessageProcessorEndpoint
internal constructor(val messageProcessor: MessageProcessor) : OutgoingMessageProcessor {
  fun onMessageReceived(message: MessageBase) {
    message.modeId = modeId!!
    messageProcessor.processIncomingMessage(onFinalizeMessage(message))
  }

  protected abstract fun onFinalizeMessage(message: MessageBase): MessageBase

  abstract val modeId: ConnectionMode?

  abstract suspend fun sendMessage(message: MessageBase): Result<Unit>

  class NotReadyException : Exception()

  class OutgoingMessageSendingException internal constructor(e: Exception?) : Exception(e)
}
