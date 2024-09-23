package it.vandillen.tracker.services

val NO_PRIORITY = "unknown"

data class MessageModel(
    var messageType: MessageType,
    var priority: String? = NO_PRIORITY,
    var end: Long? = null,
    var duration: Long? = null,
    var textMessage: String? = null,
    var pause: Boolean? = null,
    var update: Boolean? = null,
    var freeze: Boolean? = null,
    var locationRequest: Boolean? = null
)

enum class MessageType(val value: String) {
    SET("set"),
    START("start"),
    CHANGE("change"),
    STOP("stop"),
    MESSAGE("message"),
    PAUSE("pause"),
    UPDATE("update"),
    FREEZE("freeze"),
    LOCATION_REQUEST("locationRequest"),
    UNKNOWN("unknown");

    companion object {
        private val map = values().associateBy(MessageType::value)

        fun fromString(value: String?): MessageType {
            return map[value] ?: MessageType.UNKNOWN
        }
    }
}
