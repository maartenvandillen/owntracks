package it.vandillen.tracker.model.messages

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonTypeInfo
import it.vandillen.tracker.preferences.Preferences
import it.vandillen.tracker.support.MessageWaypointCollection

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "_type")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class MessageWaypoints(private val messageWithId: MessageWithId = MessageWithRandomId()) :
    MessageBase(), MessageWithId by messageWithId {
  var waypoints: it.vandillen.tracker.support.MessageWaypointCollection? = null

  override fun toString(): String = "[MessageWaypoints waypoints=${waypoints?.size}]"

  override fun annotateFromPreferences(preferences: Preferences) {
    topic = preferences.pubTopicWaypoints
    qos = preferences.pubQosWaypoints.value
    retained = preferences.pubRetainWaypoints
  }

  companion object {
    const val TYPE = "waypoints"
  }
}
