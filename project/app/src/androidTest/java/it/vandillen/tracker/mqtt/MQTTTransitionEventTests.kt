package it.vandillen.tracker.mqtt

import android.app.Notification
import android.app.NotificationManager
import android.content.Context.NOTIFICATION_SERVICE
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.adevinta.android.barista.interaction.BaristaDrawerInteractions.openDrawer
import com.adevinta.android.barista.interaction.BaristaEditTextInteractions.writeTo
import java.time.Instant
import mqtt.packets.Qos
import mqtt.packets.mqtt.MQTTPublish
import mqtt.packets.mqttv5.MQTT5Properties
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import it.vandillen.tracker.R
import it.vandillen.tracker.model.Parser
import it.vandillen.tracker.model.messages.MessageCard
import it.vandillen.tracker.model.messages.MessageLocation
import it.vandillen.tracker.model.messages.MessageTransition
import it.vandillen.tracker.testutils.GPSMockDeviceLocation
import it.vandillen.tracker.testutils.MockDeviceLocation
import it.vandillen.tracker.testutils.TestWithAnActivity
import it.vandillen.tracker.testutils.TestWithAnMQTTBroker
import it.vandillen.tracker.testutils.TestWithAnMQTTBrokerImpl
import it.vandillen.tracker.testutils.disableHeadsupNotifications
import it.vandillen.tracker.testutils.grantMapActivityPermissions
import it.vandillen.tracker.testutils.reportLocationFromMap
import it.vandillen.tracker.testutils.setNotFirstStartPreferences
import it.vandillen.tracker.testutils.stopAndroidSetupProcess
import it.vandillen.tracker.testutils.use
import it.vandillen.tracker.testutils.waitUntilActivityVisible
import it.vandillen.tracker.ui.clickOnAndWait
import it.vandillen.tracker.ui.map.MapActivity
import timber.log.Timber

@ExperimentalUnsignedTypes
@LargeTest
@RunWith(AndroidJUnit4::class)
class MQTTTransitionEventTests :
    TestWithAnActivity<MapActivity>(MapActivity::class.java, false),
    TestWithAnMQTTBroker by TestWithAnMQTTBrokerImpl(),
    MockDeviceLocation by GPSMockDeviceLocation() {

  @Before
  fun clearLocalData() {
    app.filesDir.listFiles()?.forEach { it.delete() }
  }

  @Before
  fun stopAndroidSetup() {
    stopAndroidSetupProcess()
    disableHeadsupNotifications()
  }

  private fun setupTestActivity() {
    setNotFirstStartPreferences()
    launchActivity()
    grantMapActivityPermissions()
    configureMQTTConnectionToLocalWithGeneratedPassword()
    waitUntilActivityVisible<MapActivity>()
    waitForMQTTToCompleteAndContactsToBeCleared()
  }

  @Test
  fun given_an_MQTT_configured_client_when_the_broker_sends_a_transition_message_then_a_notification_appears() {
    setupTestActivity()

    listOf(
            MessageLocation().apply {
              latitude = 52.123
              longitude = 0.56789
              trackerId = "tt"
              timestamp = Instant.parse("2006-01-02T15:04:05Z").epochSecond
            },
            MessageTransition().apply {
              accuracy = 48
              description = "Transition!"
              event = "enter"
              latitude = 52.12
              longitude = 0.56
              trigger = "l"
              trackerId = "aa" // This is the trackerId of the *waypoint*
              timestamp = Instant.parse("2006-01-02T15:04:05Z").epochSecond
            })
        .map {
          app.messageReceivedIdlingResource.add(it)
          it
        }
        .map(Parser(null)::toJsonBytes)
        .forEach {
          broker.publish(
              false,
              "owntracks/someuser/somedevice",
              Qos.AT_LEAST_ONCE,
              MQTT5Properties(),
              it.toUByteArray())
        }
    app.messageReceivedIdlingResource.use { Espresso.onIdle() }

    val notificationManager = app.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    notificationManager.activeNotifications.forEach {
      Timber.d(
          "Notification Title: ${it.notification.extras.getString(Notification.EXTRA_TITLE)} Lines: ${it.notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(separator = "|")}")
    }

    assertTrue(
        "Event notification is displayed",
        notificationManager.activeNotifications.any {
          it.notification.extras.getString(Notification.EXTRA_TITLE) == "Events" &&
              it.notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.any { line
                ->
                line.toString() == "2006-01-02 15:04 tt enters Transition!"
              } ?: false
        })
  }

  @Test
  fun given_an_MQTT_configured_client_when_the_broker_sends_a_transition_message_for_a_contact_with_a_card_then_a_notification_appears() {
    setupTestActivity()

    listOf(
            MessageCard().apply { name = "Test Contact" },
            MessageLocation().apply {
              latitude = 52.123
              longitude = 0.56789
              trackerId = "tt"
              timestamp = Instant.parse("2006-01-02T15:04:05Z").epochSecond
            },
            MessageTransition().apply {
              accuracy = 48
              description = "Transition!"
              event = "enter"
              latitude = 52.12
              longitude = 0.56
              trigger = "l"
              trackerId = "aa"
              timestamp = Instant.parse("2006-01-02T15:04:05Z").epochSecond
            })
        .map {
          app.messageReceivedIdlingResource.add(it)
          it
        }
        .map(Parser(null)::toJsonBytes)
        .forEach {
          broker.publish(
              false,
              "owntracks/someuser/somedevice",
              Qos.AT_LEAST_ONCE,
              MQTT5Properties(),
              it.toUByteArray())
        }
    app.messageReceivedIdlingResource.use { Espresso.onIdle() }

    val notificationManager = app.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    notificationManager.activeNotifications.forEach {
      Timber.d(
          "Notification Title: ${it.notification.extras.getString(Notification.EXTRA_TITLE)} Lines: ${it.notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString(separator = "|")}")
    }
    assertTrue(
        "Event notification is displayed",
        notificationManager.activeNotifications.any {
          it.notification.extras.getString(Notification.EXTRA_TITLE) == "Events" &&
              it.notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.any { line
                ->
                line.toString() == "2006-01-02 15:04 Test Contact enters Transition!"
              } ?: false
        })
  }

  @Test
  fun given_an_MQTT_configured_client_when_the_location_enters_a_geofence_a_transition_message_is_sent() {
    val waypointLatitude = 48.0
    val waypointLongitude = -1.0
    val waypointDescription = "Test Region"

    setupTestActivity()
    initializeMockLocationProvider(app)
    reportLocationFromMap(app.mockLocationIdlingResource) { setMockLocation(51.0, 0.0) }

    openDrawer()
    clickOnAndWait(R.string.title_activity_waypoints)
    clickOnAndWait(R.id.add)

    writeTo(R.id.description, waypointDescription)
    writeTo(R.id.latitude, waypointLatitude.toString())
    writeTo(R.id.longitude, waypointLongitude.toString())
    writeTo(R.id.radius, "100")

    clickOnAndWait(R.id.save)
    reportLocationFromMap(app.mockLocationIdlingResource) {
      setMockLocation(waypointLatitude, waypointLongitude)
    }

    assertTrue(
        "Packet has been received that is a transition message with the correct details",
        mqttPacketsReceived
            .filterIsInstance<MQTTPublish>()
            .map { Pair(it.topicName, Parser(null).fromJson((it.payload)!!.toByteArray())) }
            .any {
              it.second.let { message ->
                message is MessageTransition &&
                    message.description == waypointDescription &&
                    message.latitude == waypointLatitude &&
                    message.longitude == waypointLongitude &&
                    message.event == "enter"
              } && it.first == "owntracks/$mqttUsername/$deviceId/event"
            })
  }
}
