package it.vandillen.tracker.model.messages

import it.vandillen.tracker.model.Parser
import it.vandillen.tracker.preferences.Preferences

class MessageClear : MessageBase(), MessageWithId {
  override var messageId: MessageId = ZeroMessageId

  override fun toString(): String = "[MessageClear]"

  override fun annotateFromPreferences(preferences: Preferences) {
    retained = true
  }

  // Clear messages are implemented as empty messages
  override fun toJsonBytes(parser: Parser): ByteArray {
    return ByteArray(0)
  }

  override fun toJson(parser: Parser): String {
    return ""
  }
}
